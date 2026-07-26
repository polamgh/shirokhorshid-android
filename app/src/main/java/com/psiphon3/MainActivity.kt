package com.psiphon3

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.os.Bundle
import android.text.util.Linkify
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rxjava2.subscribeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import net.grandcentrix.tray.AppPreferences
import com.psiphon3.azadi.*
import com.psiphon3.log.LogsMaintenanceWorker
import com.psiphon3.psiphonlibrary.*
import com.psiphon3.ui.*
import com.psiphon3.ui.azadi.*
import com.psiphon3.ui.theme.AppColors
import com.psiphon3.ui.theme.AzadiTunnelTheme
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable

class MainActivity : LocalizedActivities.AppCompatActivity() {

    private lateinit var viewModel: MainActivityViewModel
    private val compositeDisposable = CompositeDisposable()
    private var multiProcessPreferences: AppPreferences? = null
    private lateinit var azadiSettingsStore: AzadiSettingsStore
    private lateinit var connectionCoordinator: ConnectionCoordinator
    private lateinit var findBestController: FindBestConnectionController
    private lateinit var persistentTrafficStatsStore: PersistentTrafficStatsStore
    private var invalidProxySettingsToast: Toast? = null
    private var upstreamProxyErrorAlertDialog: AlertDialog? = null

    companion object {
        const val REQUEST_CODE_PERMISSIONS = 103
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Utils.initializeSecureRandom()
        EmbeddedValues.initialize(applicationContext)
        VpnRulesHelper.configureRuntimeVpnRules(
            VpnRulesHelper.readVpnRulesFromFile(applicationContext)
        )
        multiProcessPreferences = AppPreferences(this)
        azadiSettingsStore = AzadiSettingsStore(this)
        TunnelStatisticsStore.init(this)
        connectionCoordinator = ConnectionCoordinator(
            context = this,
            settingsStore = azadiSettingsStore,
            prefs = multiProcessPreferences!!,
            protocolKey = getString(R.string.protocolSelectionPreference),
            beastKey = getString(R.string.beastModePreference)
        )
        findBestController = FindBestConnectionController(
            settingsStore = azadiSettingsStore,
            prefs = multiProcessPreferences!!,
            protocolKey = getString(R.string.protocolSelectionPreference),
            beastKey = getString(R.string.beastModePreference),
            regionKey = getString(R.string.egressRegionPreference)
        )
        persistentTrafficStatsStore = PersistentTrafficStatsStore(this)
        AzadiEventLogger.logSync("APP_BOOT", "version=${BuildConfig.VERSION_NAME} build=${BuildConfig.VERSION_CODE}")
        BundledServerEntries.ensureLoaded(applicationContext)
        ConfigManager.ensureBundledConfig(applicationContext)
        SettingsPreferencesMigrator.migrateVpnSettings(this, multiProcessPreferences!!)
        SettingsPreferencesMigrator.migrateProxySettings(this, multiProcessPreferences!!)
        multiProcessPreferences!!.put(getString(R.string.autoOpenHomepagePreference), false)

        viewModel = ViewModelProvider(this,
            ViewModelProvider.AndroidViewModelFactory(application))
            .get(MainActivityViewModel::class.java)
        lifecycle.addObserver(viewModel)

        LogsMaintenanceWorker.schedule(applicationContext)

        val startupLanguage = azadiSettingsStore.load().preferredLanguage.ifEmpty { "system" }
        AppLocaleHelper.applyToActivity(this, startupLanguage)

        if (savedInstanceState == null) {
            handleCurrentIntent(intent)
            checkPermissions()
        }

        setContent {
            AzadiTunnelTheme {
                val initialSettings = remember { azadiSettingsStore.load() }
                var appLanguage by remember {
                    mutableStateOf(initialSettings.preferredLanguage.ifEmpty { "system" })
                }

                LaunchedEffect(Unit) {
                    val saved = azadiSettingsStore.load().preferredLanguage.ifEmpty { "system" }
                    if (saved != appLanguage) {
                        appLanguage = saved
                    }
                    AppLocaleHelper.applyToActivity(this@MainActivity, appLanguage)
                }

                ProvideAppLocale(appLanguage) {
                val scope = rememberCoroutineScope()
                var currentTab by rememberSaveable { mutableIntStateOf(0) }
                var settingsDestination by remember { mutableStateOf(SettingsDestination.ROOT) }
                var azadiSettings by remember { mutableStateOf(initialSettings) }

                var launchPhase by remember {
                    mutableStateOf(
                        when {
                            !initialSettings.hasChosenLanguage -> LaunchPhase.LANGUAGE
                            !initialSettings.hasCompletedOnboarding -> LaunchPhase.SPLASH
                            else -> LaunchPhase.DONE
                        }
                    )
                }
                var showDisclaimer by remember { mutableStateOf(false) }

                val connectionUi by connectionCoordinator.uiState.collectAsState()
                val findBestState by findBestController.state.collectAsState()
                val tunnelStats by TunnelStatisticsStore.flow.collectAsState()

                LaunchedEffect(Unit) {
                    while (true) {
                        TunnelStatisticsStore.reload(this@MainActivity)
                        delay(1000)
                    }
                }

                val tunnelStateState = tunnelStateFlowable().subscribeAsState(initial = TunnelState.stopped())
                val tunnelState = tunnelStateState.value ?: TunnelState.stopped()

                var ipAddress by remember { mutableStateOf("—") }
                var connectedCity by remember { mutableStateOf("") }
                var connectedCountry by remember { mutableStateOf("") }
                var durationText by remember { mutableStateOf("00:00:00") }

                val selectedRegionCode = azadiSettings.egressRegion.ifEmpty { PsiphonConstants.REGION_CODE_ANY }

                val (initialDownload, initialUpload) = remember { persistentTrafficStatsStore.load() }
                var lifetimeDownload by remember { mutableLongStateOf(initialDownload) }
                var lifetimeUpload by remember { mutableLongStateOf(initialUpload) }
                var lastSessionDownload by remember { mutableLongStateOf(0L) }
                var lastSessionUpload by remember { mutableLongStateOf(0L) }

                val dataStatsState = dataStatsFlowable().subscribeAsState(initial = 0L)
                val dataStats = dataStatsState.value ?: 0L

                val baseStatus = deriveConnectionStatus(tunnelState)
                val connectionStatus = if (!connectionUi.errorMessage.isNullOrBlank() && baseStatus == VpnConnectionStatus.DISCONNECTED) {
                    VpnConnectionStatus.ERROR
                } else baseStatus

                var geoLookupAttempt by remember { mutableIntStateOf(0) }

                LaunchedEffect(connectionStatus) {
                    TunnelStatisticsStore.reload(this@MainActivity)
                }

                val isConnected = connectionStatus == VpnConnectionStatus.CONNECTED
                val quality = connectionUi.qualityReport

                LaunchedEffect(connectionStatus, tunnelState.isRunning) {
                    if (connectionStatus == VpnConnectionStatus.CONNECTED &&
                        connectionUi.qualityReport == null &&
                        tunnelState.connectionData()?.isConnected == true
                    ) {
                        scope.launch {
                            connectionCoordinator.runPostConnectDiagnostics(tunnelState)
                        }
                    }
                }

                LaunchedEffect(connectionStatus, quality, geoLookupAttempt) {
                    if (!isConnected) {
                        ipAddress = "—"
                        connectedCity = ""
                        connectedCountry = ""
                        geoLookupAttempt = 0
                        return@LaunchedEffect
                    }

                    quality?.publicIp?.takeIf { it.isNotBlank() && it != "—" }?.let { ipAddress = it }
                    quality?.city?.takeIf { it.isNotBlank() }?.let { connectedCity = it }
                    quality?.countryName?.takeIf { it.isNotBlank() }?.let { connectedCountry = it }

                    val needsGeo = ipAddress == "—" ||
                        connectedCity.isBlank() ||
                        connectedCountry.isBlank()
                    if (!needsGeo) return@LaunchedEffect

                    delay(1200L + geoLookupAttempt * 1500L)

                    val geo = withContext(Dispatchers.IO) { PublicGeoLookup.lookup() }
                    if (geo.ip.isNotBlank() && geo.ip != "—") ipAddress = geo.ip
                    if (geo.city.isNotBlank()) connectedCity = geo.city
                    if (geo.countryName.isNotBlank()) connectedCountry = geo.countryName

                    if ((connectedCity.isBlank() || connectedCountry.isBlank()) && geo.isUsable()) {
                        val enriched = withContext(Dispatchers.IO) { PublicGeoLookup.lookupByIp(geo.ip) }
                        if (enriched.city.isNotBlank()) connectedCity = enriched.city
                        if (enriched.countryName.isNotBlank()) connectedCountry = enriched.countryName
                    }

                    if ((connectedCity.isBlank() || connectedCountry.isBlank()) && geoLookupAttempt < 2) {
                        geoLookupAttempt++
                    }
                }

                val connectedLocationLine = if (isConnected) {
                    resolveConnectedLocationLine(
                        city = quality?.city?.takeIf { it.isNotBlank() } ?: connectedCity.takeIf { it.isNotBlank() },
                        countryName = quality?.countryName?.takeIf { it.isNotBlank() }
                            ?: connectedCountry.takeIf { it.isNotBlank() },
                        clientRegionCode = null
                    ) ?: if (ipAddress != "—") {
                        stringResource(R.string.azadi_location_detecting)
                    } else {
                        null
                    }
                } else null
                val regionHintLine: String? = null

                val protocolSelection = azadiSettings.protocolSelection
                val showConduitCard = protocolSelection == "conduit" &&
                    (connectionStatus == VpnConnectionStatus.CONNECTING ||
                        connectionStatus == VpnConnectionStatus.CONNECTED) &&
                    (tunnelStats.conduitStatusLine.isNotEmpty() ||
                        tunnelStats.conduitStatusHistory.isNotEmpty())

                val displayProtocol = if (
                    connectionStatus == VpnConnectionStatus.CONNECTED &&
                    tunnelStats.connectedTunnelProtocol.isNotBlank()
                ) {
                    ConnectedTunnelProtocolParser.displayName(tunnelStats.connectedTunnelProtocol)
                        .takeIf { it.isNotBlank() }
                } else {
                    null
                }

                val statusMessage = when {
                    showConduitCard &&
                        connectionStatus == VpnConnectionStatus.CONNECTING &&
                        tunnelStats.conduitStatusLine.isNotEmpty() ->
                        tunnelStats.conduitStatusLine
                    connectionStatus == VpnConnectionStatus.CONNECTED ->
                        stringResource(R.string.dashboard_connected)
                    connectionStatus == VpnConnectionStatus.CONNECTING ->
                        stringResource(R.string.dashboard_connecting)
                    connectionStatus == VpnConnectionStatus.WAITING_FOR_NETWORK ->
                        stringResource(R.string.waiting_for_network_connectivity)
                    connectionStatus == VpnConnectionStatus.WAITING ->
                        stringResource(R.string.waiting)
                    connectionStatus == VpnConnectionStatus.ERROR ->
                        stringResource(R.string.azadi_status_error)
                    else -> stringResource(R.string.dashboard_disconnected)
                }

                val trafficStats = remember(dataStats, ipAddress, connectedCity, connectedCountry, lifetimeDownload, lifetimeUpload) {
                    TrafficStats(
                        downloadSpeed = Utils.byteCountToDisplaySize(TunnelServiceInteractor.lastDownloadSpeedBytes, true) + "/s",
                        uploadSpeed = Utils.byteCountToDisplaySize(TunnelServiceInteractor.lastUploadSpeedBytes, true) + "/s",
                        totalDownload = Utils.byteCountToDisplaySize(lifetimeDownload, false),
                        totalUpload = Utils.byteCountToDisplaySize(lifetimeUpload, false),
                        ipAddress = ipAddress,
                        connectedCountry = connectedCountry
                    )
                }

                val savedBest = findBestController.loadSavedBest()
                val savedBestLabel = savedBest?.let {
                    "${it.protocol} @ ${it.region} · ${"%.1f".format(it.speedMbps)} Mbps"
                }

                LaunchedEffect(connectionStatus) {
                    if (connectionStatus != VpnConnectionStatus.CONNECTED) {
                        lastSessionDownload = 0L
                        lastSessionUpload = 0L
                    }
                }

                LaunchedEffect(dataStats, connectionStatus) {
                    if (connectionStatus == VpnConnectionStatus.CONNECTED) {
                        val stats = DataTransferStats.getDataTransferStatsForUI()
                        val result = persistentTrafficStatsStore.accumulateSessionDelta(
                            lifetimeDownload = lifetimeDownload,
                            lifetimeUpload = lifetimeUpload,
                            sessionDownload = stats.totalBytesReceived,
                            sessionUpload = stats.totalBytesSent,
                            lastSessionDownload = lastSessionDownload,
                            lastSessionUpload = lastSessionUpload
                        )
                        if (result.changed) {
                            lifetimeDownload = result.lifetimeDownload
                            lifetimeUpload = result.lifetimeUpload
                        }
                        lastSessionDownload = result.lastSessionDownload
                        lastSessionUpload = result.lastSessionUpload
                    }
                }

                LaunchedEffect(tunnelState, connectionUi.diagnosticsSummary) {
                    val isConnectedNow = baseStatus == VpnConnectionStatus.CONNECTED
                    updatePsiphonBumpHceState(isConnectedNow)
                }

                LaunchedEffect(connectionStatus) {
                    if (connectionStatus == VpnConnectionStatus.CONNECTED) {
                        connectionCoordinator.refreshPing()
                        while (true) {
                            delay(5000)
                            connectionCoordinator.refreshPing()
                        }
                    }
                }

                LaunchedEffect(connectionStatus, dataStats) {
                    if (connectionStatus == VpnConnectionStatus.CONNECTED) {
                        while (true) {
                            durationText = Utils.elapsedTimeToDisplay(
                                DataTransferStats.getDataTransferStatsForUI().elapsedTime
                            )
                            delay(1000)
                        }
                    } else {
                        durationText = "00:00:00"
                    }
                }

                LaunchedEffect(azadiSettings.connectOnLaunch, azadiSettings.hasChosenLanguage) {
                    if (azadiSettings.hasChosenLanguage && azadiSettings.connectOnLaunch &&
                        baseStatus == VpnConnectionStatus.DISCONNECTED &&
                        azadiSettingsStore.hasActivePsiphonConfig()
                    ) {
                        delay(1500)
                        if (azadiSettings.hasAcceptedConnectionDisclaimer) {
                            scope.launch { performConnect() }
                        }
                    }
                }

                LaunchedEffect(azadiSettings.autoReconnect, baseStatus) {
                    if (!azadiSettings.autoReconnect) return@LaunchedEffect
                    while (true) {
                        delay(5000)
                        val state = tunnelStateState.value ?: continue
                        if (state.isRunning && state.connectionData()?.isConnected != true) continue
                        if (!state.isRunning && baseStatus == VpnConnectionStatus.CONNECTED) {
                            AzadiEventLogger.logSync("AUTO_RECONNECT_TRIGGERED")
                            val ok = performConnect()
                            if (ok) AzadiEventLogger.logSync("AUTO_RECONNECT_SUCCESS")
                            else AzadiEventLogger.logSync("AUTO_RECONNECT_FAILED")
                        }
                    }
                }

                val showSettingsSubScreen = settingsDestination != SettingsDestination.ROOT &&
                        settingsDestination != SettingsDestination.LOGS

                val inLaunchFlow = launchPhase != LaunchPhase.DONE

                val showBottomNav = !showSettingsSubScreen && settingsDestination != SettingsDestination.LOGS &&
                        !inLaunchFlow

                val context = LocalContext.current
                var pendingExit by remember { mutableStateOf(false) }

                LaunchedEffect(pendingExit) {
                    if (pendingExit) {
                        delay(2000)
                        pendingExit = false
                    }
                }

                BackHandler(
                    enabled = !inLaunchFlow && !showDisclaimer
                ) {
                    when {
                        settingsDestination == SettingsDestination.LOGS -> {
                            settingsDestination = SettingsDestination.ROOT
                        }
                        settingsDestination != SettingsDestination.ROOT -> {
                            settingsDestination = SettingsDestination.ROOT
                        }
                        currentTab == 1 -> {
                            currentTab = 0
                            settingsDestination = SettingsDestination.ROOT
                        }
                        pendingExit -> finish()
                        else -> {
                            pendingExit = true
                            Toast.makeText(
                                context,
                                R.string.azadi_press_back_again_to_exit,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                Scaffold(
                    containerColor = AppColors.Background,
                    bottomBar = {}
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .background(AppColors.Background)
                    ) {
                        when {
                            settingsDestination == SettingsDestination.LOGS -> AzadiLogsScreen(
                                viewModel = viewModel,
                                onBack = { settingsDestination = SettingsDestination.ROOT },
                                onExport = {
                                    val report = DebugReportExporter.export(
                                        this@MainActivity,
                                        azadiSettingsStore.load(),
                                        tunnelState,
                                        connectionUi
                                    )
                                    copyToClipboard(report)
                                    Toast.makeText(this@MainActivity, R.string.azadi_export_debug, Toast.LENGTH_SHORT).show()
                                }
                            )
                            settingsDestination == SettingsDestination.SUPPORT -> AzadiSupportScreen(
                                onBack = { settingsDestination = SettingsDestination.ROOT }
                            )
                            currentTab == 0 && !inLaunchFlow -> MainScreen(
                                connectionStatus = connectionStatus,
                                statusMessage = statusMessage,
                                selectedRegionCode = selectedRegionCode,
                                connectedLocationLine = connectedLocationLine,
                                regionHintLine = regionHintLine,
                                durationText = durationText,
                                connectedProtocol = displayProtocol,
                                pingMs = connectionUi.pingMs,
                                errorMessage = connectionUi.errorMessage,
                                diagnosticsSummary = connectionUi.diagnosticsSummary,
                                leakSummary = connectionUi.leakSummary,
                                proxyOnlyAddress = connectionUi.proxyOnlyAddress,
                                conduitStatusLine = tunnelStats.conduitStatusLine,
                                conduitStatusHistory = tunnelStats.conduitStatusHistory,
                                showConduitCard = showConduitCard,
                                findBestRunning = findBestState.running,
                                findBestProgress = findBestState.progress,
                                savedBestLabel = savedBestLabel,
                                onToggleClick = {
                                    if (!azadiSettings.hasAcceptedConnectionDisclaimer) {
                                        AzadiEventLogger.logSync("DISCLAIMER_PRESENTED")
                                        showDisclaimer = true
                                    } else {
                                        scope.launch { toggleTunnel() }
                                    }
                                },
                                onRegionSelected = { regionCode ->
                                    onRegionSelected(regionCode)
                                    azadiSettings = azadiSettings.copy(egressRegion = regionCode)
                                },
                                onRefreshPing = { scope.launch { connectionCoordinator.refreshPing() } },
                                onFindBestClick = {
                                    scope.launch {
                                        findBestController.runScan(
                                            minSpeedMbps = 2,
                                            conduitAllowed = azadiSettingsStore.conduitConnectAllowed(),
                                            startTunnel = { startTunnelForConnect() },
                                            stopTunnel = { getTunnelServiceInteractor().stopTunnelService() },
                                            awaitTunnelState = { awaitTunnelState() },
                                            measureSpeedMbps = { withContext(Dispatchers.IO) { NetworkUtils.measureDownloadSpeedMbps() } }
                                        )
                                    }
                                },
                                onConnectBestClick = {
                                    azadiSettings = findBestController.applyBest()
                                    scope.launch { performConnect() }
                                },
                                onRetryClick = { scope.launch { performConnect() } },
                                onOpenLogs = { settingsDestination = SettingsDestination.LOGS },
                                onSupportClick = { settingsDestination = SettingsDestination.SUPPORT },
                                trafficStats = trafficStats
                            )
                            currentTab == 1 && !inLaunchFlow -> AzadiSettingsScreen(
                                viewModel = viewModel,
                                settingsStore = azadiSettingsStore,
                                settings = azadiSettings,
                                destination = settingsDestination,
                                vpnConnected = connectionStatus == VpnConnectionStatus.CONNECTED,
                                tunnelState = tunnelState,
                                connectionUi = connectionUi,
                                onDestinationChange = { dest ->
                                    settingsDestination = if (dest == SettingsDestination.LOGS) SettingsDestination.LOGS else dest
                                },
                                onSettingsChanged = { updated, reconnect ->
                                    azadiSettings = updated
                                    if (reconnect) {
                                        scope.launch {
                                            val interactor = getTunnelServiceInteractor()
                                            val wasRunning = awaitTunnelState().isRunning
                                            if (wasRunning) {
                                                SettingsReconnectHelper.reconnectIfConnected(
                                                    isRunning = { true },
                                                    disconnect = { interactor.stopTunnelService() },
                                                    connect = { performConnect() }
                                                )
                                            }
                                        }
                                    }
                                },
                                onExportDebug = {
                                    val report = DebugReportExporter.export(
                                        this@MainActivity,
                                        azadiSettingsStore.load(),
                                        tunnelState,
                                        connectionUi
                                    )
                                    copyToClipboard(report)
                                    Toast.makeText(this@MainActivity, R.string.azadi_export_debug, Toast.LENGTH_SHORT).show()
                                },
                                onLanguageSelected = { code ->
                                    applyLanguage(code) { updated ->
                                        appLanguage = updated.preferredLanguage
                                        azadiSettings = updated
                                    }
                                }
                            )
                        }

                        when (launchPhase) {
                            LaunchPhase.LANGUAGE -> LanguageSelectionScreen(
                                onSelectEnglish = {
                                    applyLanguage("en") { updated ->
                                        appLanguage = updated.preferredLanguage
                                        azadiSettings = updated
                                        launchPhase = LaunchPhase.SPLASH
                                    }
                                },
                                onSelectPersian = {
                                    applyLanguage("fa") { updated ->
                                        appLanguage = updated.preferredLanguage
                                        azadiSettings = updated
                                        launchPhase = LaunchPhase.SPLASH
                                    }
                                }
                            )
                            LaunchPhase.SPLASH -> SplashScreen(onFinished = {
                                launchPhase = if (!azadiSettings.hasCompletedOnboarding) {
                                    LaunchPhase.ONBOARDING
                                } else {
                                    LaunchPhase.DONE
                                }
                            })
                            LaunchPhase.ONBOARDING -> {
                                val completeOnboarding: () -> Unit = {
                                    azadiSettings = azadiSettingsStore.saveField(azadiSettings) {
                                        it.copy(hasCompletedOnboarding = true)
                                    }
                                    launchPhase = LaunchPhase.DONE
                                }
                                OnboardingScreen(
                                    onComplete = completeOnboarding,
                                    onSkip = completeOnboarding
                                )
                            }
                            LaunchPhase.DONE -> Unit
                        }

                        if (showBottomNav) {
                            AzadiFloatingBottomNav(
                                selectedTab = currentTab,
                                onVpnClick = { currentTab = 0; settingsDestination = SettingsDestination.ROOT },
                                onSettingsClick = { currentTab = 1; settingsDestination = SettingsDestination.ROOT },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }

                        if (showDisclaimer) {
                            ConnectionDisclaimerSheet(
                                onAccept = {
                                    AzadiEventLogger.logSync("DISCLAIMER_ACCEPTED")
                                    azadiSettings = azadiSettingsStore.saveField(azadiSettings) {
                                        it.copy(
                                            hasAcceptedConnectionDisclaimer = true,
                                            hasAcceptedVPNDisclosure = true
                                        )
                                    }
                                    showDisclaimer = false
                                    scope.launch { toggleTunnel() }
                                },
                                onCancel = {
                                    AzadiEventLogger.logSync("DISCLAIMER_CANCELLED")
                                    showDisclaimer = false
                                }
                            )
                        }
                    }
                }
                }
            }
        }
    }

    private fun applyLanguage(code: String, onSaved: (AzadiSettings) -> Unit) {
        val lang = code.ifEmpty { "system" }
        AzadiEventLogger.logSync("LANGUAGE_CHOSEN", "language=$lang")
        val updated = azadiSettingsStore.saveField(azadiSettingsStore.load()) {
            it.copy(hasChosenLanguage = true, preferredLanguage = lang)
        }
        AppLocaleHelper.applyToActivity(this, lang)
        onSaved(updated)
    }

    private suspend fun awaitTunnelState(): TunnelState = withContext(Dispatchers.IO) {
        getTunnelServiceInteractor().tunnelStateFlowable().firstOrError().blockingGet()
    }

    private suspend fun toggleTunnel() {
        val interactor = getTunnelServiceInteractor()
        val state = withContext(Dispatchers.IO) {
            interactor.tunnelStateFlowable()
                .filter { !it.isUnknown }
                .firstOrError()
                .blockingGet()
        }
        if (state.isRunning) {
            connectionCoordinator.disconnect(interactor)
        } else {
            performConnect()
        }
    }

    private suspend fun startTunnelForConnect(): Boolean = suspendCancellableCoroutine { cont ->
        startTunnel(object : LocalizedActivities.StartServiceListener {
            override fun onServiceStartOk() {
                if (cont.isActive) cont.resume(true)
            }

            override fun onServiceStartCancelled() {
                if (cont.isActive) cont.resume(false)
            }
        })
    }

    private suspend fun performConnect(): Boolean {
        if (!azadiSettingsStore.load().hasAcceptedConnectionDisclaimer) {
            AzadiEventLogger.logSync("CONNECT_BLOCKED_PENDING_DISCLAIMER")
            return false
        }
        return connectionCoordinator.connect(
            interactor = getTunnelServiceInteractor(),
            settings = azadiSettingsStore.load(),
            validateProxy = { viewModel.validateCustomProxySettings() },
            startTunnel = { startTunnelForConnect() },
            stopTunnel = { getTunnelServiceInteractor().stopTunnelService() },
            awaitTunnelState = { awaitTunnelState() }
        )
    }

    @Composable
    private fun navColors() = NavigationBarItemDefaults.colors(
        selectedIconColor = AppColors.IranGreenBright,
        selectedTextColor = Color.White,
        indicatorColor = AppColors.IranGreen.copy(alpha = 0.2f),
        unselectedIconColor = Color.Gray,
        unselectedTextColor = Color.Gray
    )

    private fun deriveConnectionStatus(tunnelState: TunnelState): VpnConnectionStatus = when {
        tunnelState.isUnknown -> VpnConnectionStatus.WAITING
        !tunnelState.isRunning -> VpnConnectionStatus.DISCONNECTED
        tunnelState.connectionData()?.isConnected == true -> VpnConnectionStatus.CONNECTED
        tunnelState.connectionData()?.networkConnectionState() ==
            TunnelState.ConnectionData.NetworkConnectionState.WAITING_FOR_NETWORK ->
            VpnConnectionStatus.WAITING_FOR_NETWORK
        else -> VpnConnectionStatus.CONNECTING
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCurrentIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        compositeDisposable.add(
            viewModel.customProxyValidationResultFlowable()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { isValid ->
                    if (!isValid) {
                        cancelInvalidProxySettingsToast()
                        invalidProxySettingsToast = Toast.makeText(
                            this, R.string.network_proxy_connect_invalid_values, Toast.LENGTH_SHORT
                        )
                        invalidProxySettingsToast?.show()
                    }
                }
                .subscribe()
        )
    }

    override fun onPause() {
        cancelInvalidProxySettingsToast()
        compositeDisposable.clear()
        super.onPause()
    }

    private fun cancelInvalidProxySettingsToast() {
        invalidProxySettingsToast?.cancel()
        invalidProxySettingsToast = null
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AzadiTunnel debug", text))
    }

    private fun handleCurrentIntent(intent: Intent?) {
        if (intent == null || intent.action == null) return
        val handler = ComponentName(this, "com.psiphon3.psiphonlibrary.TunnelIntentsHandler")
        if (handler != intent.component) return

        when (intent.action) {
            TunnelManager.INTENT_ACTION_SELECTED_REGION_NOT_AVAILABLE -> {
                viewModel.signalAvailableRegionsUpdate()
                Toast.makeText(this, R.string.selected_region_currently_not_available, Toast.LENGTH_LONG)
                    .apply { setGravity(Gravity.CENTER, 0, 0) }.show()
            }
            TunnelManager.INTENT_ACTION_VPN_REVOKED -> {
                showVpnAlertDialog(R.string.StatusActivity_VpnRevokedTitle, R.string.StatusActivity_VpnRevokedMessage)
            }
            TunnelManager.INTENT_ACTION_UNSAFE_TRAFFIC -> {
                if (!isFinishing) showUnsafeTrafficDialog(intent)
            }
            TunnelManager.INTENT_ACTION_UPSTREAM_PROXY_ERROR -> {
                if ((upstreamProxyErrorAlertDialog == null || upstreamProxyErrorAlertDialog?.isShowing != true) && !isFinishing) {
                    upstreamProxyErrorAlertDialog = AlertDialog.Builder(this)
                        .setIcon(R.drawable.ic_psiphon_alert_notification)
                        .setTitle(R.string.upstream_proxy_error_alert_title)
                        .setMessage(R.string.upstream_proxy_error_alert_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .create()
                    upstreamProxyErrorAlertDialog?.show()
                }
            }
        }
    }

    private fun showUnsafeTrafficDialog(intent: Intent) {
        val extras = intent.extras
        val dialogView = LayoutInflater.from(this).inflate(R.layout.unsafe_traffic_alert_layout, null)
        val tv = dialogView.findViewById<TextView>(R.id.textView)
        extras?.getStringArrayList(TunnelManager.DATA_UNSAFE_TRAFFIC_SUBJECTS_LIST)?.forEach {
            tv.append("\n$it")
        }
        extras?.getStringArrayList(TunnelManager.DATA_UNSAFE_TRAFFIC_ACTION_URLS_LIST)?.forEach {
            tv.append("\n$it")
        }
        Linkify.addLinks(tv, Linkify.WEB_URLS)
        AlertDialog.Builder(this)
            .setIcon(R.drawable.ic_psiphon_alert_notification)
            .setTitle(R.string.unsafe_traffic_alert_dialog_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun updatePsiphonBumpHceState(isConnected: Boolean) {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(this) ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
        ) return
        val cardEmulation = CardEmulation.getInstance(nfcAdapter)
        val component = ComponentName(this, PsiphonHostApduService::class.java)
        if (isConnected) {
            cardEmulation.registerAidsForService(component, CardEmulation.CATEGORY_OTHER, listOf("50736970686f6e4e6663"))
        } else {
            cardEmulation.removeAidsForService(component, CardEmulation.CATEGORY_OTHER)
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PermissionChecker.PERMISSION_GRANTED
        ) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PermissionChecker.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun tunnelStateFlowable() = getTunnelServiceInteractor().tunnelStateFlowable()
        .observeOn(AndroidSchedulers.mainThread())

    private fun dataStatsFlowable() = getTunnelServiceInteractor().dataStatsFlowable()
        .observeOn(AndroidSchedulers.mainThread())

    private fun onRegionSelected(regionCode: String) {
        val tray = multiProcessPreferences ?: return
        val key = getString(R.string.egressRegionPreference)
        if (regionCode == tray.getString(key, PsiphonConstants.REGION_CODE_ANY)) return
        tray.put(key, regionCode)
        azadiSettingsStore.save(azadiSettingsStore.load().copy(egressRegion = regionCode))

        compositeDisposable.add(
            getTunnelServiceInteractor().tunnelStateFlowable()
                .filter { !it.isUnknown }
                .take(1)
                .doOnNext { state ->
                    if (state.isRunning) getTunnelServiceInteractor().commandTunnelRestart()
                }
                .subscribe()
        )
    }

    override fun onDestroy() {
        compositeDisposable.dispose()
        super.onDestroy()
    }
}
