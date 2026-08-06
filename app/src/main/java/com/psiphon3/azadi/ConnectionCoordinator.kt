package com.psiphon3.azadi

import android.content.Context
import com.psiphon3.TunnelState
import com.psiphon3.psiphonlibrary.TunnelServiceInteractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.grandcentrix.tray.AppPreferences

data class ConnectionUiState(
    val fallbackStepLabel: String? = null,
    val connectedProtocol: String? = null,
    val errorMessage: String? = null,
    val diagnosticsSummary: String? = null,
    val pingMs: Long = -1,
    val findBestRunning: Boolean = false,
    val findBestProgress: String? = null,
    val proxyOnlyAddress: String? = null,
    val qualityReport: QualityReport? = null,
    val leakSummary: String? = null
)

class ConnectionCoordinator(
    private val context: Context,
    private val settingsStore: AzadiSettingsStore,
    private val prefs: AppPreferences,
    private val protocolKey: String,
    private val beastKey: String
) {
    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    var skipRecursiveFallback: Boolean = false

    suspend fun disconnect(interactor: TunnelServiceInteractor) {
        AzadiEventLogger.log("VPN_DISCONNECT_REQUESTED")
        FirebaseAnalyticsManager.logVpnDisconnected()
        TunnelStatisticsStore.markDisconnected(context)
        _uiState.value = _uiState.value.copy(
            connectedProtocol = null,
            errorMessage = null,
            diagnosticsSummary = null,
            fallbackStepLabel = null,
            proxyOnlyAddress = null,
            qualityReport = null,
            leakSummary = null,
            pingMs = -1
        )
        interactor.stopTunnelService(context)
        AzadiEventLogger.log("ANDROID_VPN_SERVICE_STOPPED")
        AzadiEventLogger.log("PSIPHON_STOPPED")
        AzadiEventLogger.log("TUNNEL_STOP_CLEANUP")
    }

    suspend fun connect(
        interactor: TunnelServiceInteractor,
        settings: AzadiSettings,
        validateProxy: () -> Boolean,
        startTunnel: suspend () -> Boolean,
        stopTunnel: () -> Unit,
        awaitTunnelState: suspend () -> TunnelState
    ): Boolean {
        AzadiEventLogger.log("VPN_CONNECT_REQUESTED")
        FirebaseAnalyticsManager.logVpnConnectStarted(settings.protocolSelection)

        if (!settings.hasAcceptedConnectionDisclaimer) {
            AzadiEventLogger.log("CONNECT_BLOCKED_PENDING_DISCLAIMER")
            _uiState.value = _uiState.value.copy(
                errorMessage = context.getString(com.psiphon3.R.string.azadi_disclaimer_required)
            )
            return false
        }

        if (!settingsStore.hasActivePsiphonConfig()) {
            val installed = ConfigManager.ensureBundledConfig(context)
            if (!installed) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = context.getString(com.psiphon3.R.string.azadi_no_config)
                )
                return false
            }
            AzadiEventLogger.log("PSIPHON_BUNDLED_CONFIG_INSTALLED")
        }

        if (settings.protocolSelection == "conduit" && !settingsStore.conduitConnectAllowed()) {
            AzadiEventLogger.log("CONDUIT_BLOCKED_MISSING_KEYS")
            _uiState.value = _uiState.value.copy(
                errorMessage = context.getString(com.psiphon3.R.string.azadi_conduit_blocked)
            )
            return false
        }

        if (settings.proxyOnlyModeEnabled) {
            if (!NetworkUtils.isOnWifi(context)) {
                AzadiEventLogger.log("PROXY_ONLY_BLOCKED_NO_WIFI")
                _uiState.value = _uiState.value.copy(
                    errorMessage = context.getString(com.psiphon3.R.string.azadi_proxy_only_wifi_required)
                )
                return false
            }
            val wifiIp = LocalNetworkAddress.wifiIpv4(context)
            if (wifiIp == null) {
                AzadiEventLogger.log("PROXY_ONLY_BLOCKED_NO_WIFI")
                _uiState.value = _uiState.value.copy(
                    errorMessage = context.getString(com.psiphon3.R.string.azadi_proxy_only_wifi_required)
                )
                return false
            }
            AzadiEventLogger.log("PROXY_ONLY_MODE_ENABLED")
            AzadiEventLogger.log("LAN_PROXY_WIFI_IP_DETECTED", "ip=$wifiIp")
        }

        if (settings.bypassIranIPsEnabled) {
            withContext(Dispatchers.IO) {
                IranBypassListService.refresh(context, force = false)
            }
        }
        if (settings.bypassDomains.isNotBlank()) {
            withContext(Dispatchers.IO) {
                BypassDomainResolver.resolveAndCache(context, settings.bypassDomains)
            }
        }

        if (!validateProxy()) return false

        AzadiEventLogger.log("VPN_START_REQUESTED")
        AzadiEventLogger.log("PSIPHON_CORE_SELECTED", settings.protocolSelection)

        _uiState.value = _uiState.value.copy(fallbackStepLabel = null, errorMessage = null)

        val connected = connectWithInternetVerification(
            settings,
            startTunnel,
            stopTunnel,
            awaitTunnelState
        )

        if (!connected) {
            _uiState.value = _uiState.value.copy(
                errorMessage = context.getString(com.psiphon3.R.string.azadi_no_internet)
            )
            FirebaseAnalyticsManager.logVpnConnectFailed(settings.protocolSelection, "no_internet")
            return false
        }

        val state = awaitTunnelState()
        val rawProtocol = TunnelStatisticsStore.load(context).connectedTunnelProtocol
        val protocol = ConnectedTunnelProtocolParser.displayName(rawProtocol).takeIf { it.isNotBlank() }
            ?: ProtocolDisplay.formatTransportSelection(settingsStore.load().protocolSelection)
        _uiState.value = _uiState.value.copy(
            connectedProtocol = protocol,
            errorMessage = null
        )
        AzadiEventLogger.log("PSIPHON_TUNNEL_ESTABLISHED", protocol)
        AzadiEventLogger.log("TUNNEL_CONNECTED", protocol)
        FirebaseAnalyticsManager.logVpnConnectSuccess(settings.protocolSelection, protocol)

        if (settings.proxyOnlyModeEnabled) {
            val wifiIp = LocalNetworkAddress.wifiIpv4(context)
            val httpPort = settings.lanHttpProxyPort
            val socksPort = settings.lanSocksProxyPort
            _uiState.value = _uiState.value.copy(
                proxyOnlyAddress = wifiIp?.let { "HTTP $it:$httpPort · SOCKS $it:$socksPort" }
            )
            AzadiEventLogger.log("PROXY_ONLY_WARNING_NOT_FULL_VPN")
            AzadiEventLogger.log("PROXY_ONLY_STARTED", _uiState.value.proxyOnlyAddress)
        } else {
            runPostConnectDiagnostics(state)
        }
        return true
    }

    /**
     * Connect, verify real internet (Google 204 + public IP), retry other transports if needed.
     */
    private suspend fun connectWithInternetVerification(
        settings: AzadiSettings,
        startTunnel: suspend () -> Boolean,
        stopTunnel: () -> Unit,
        awaitTunnelState: suspend () -> TunnelState
    ): Boolean {
        val original = settingsStore.load()
        val attempts = if (skipRecursiveFallback) {
            listOf(FallbackStep(original.protocolSelection, original.beastModeEnabled, 120))
        } else {
            FallbackChainController.internetAwareAttemptsFor(original)
        }

        if (attempts.size > 1) {
            AzadiEventLogger.log("INTERNET_VERIFY_CHAIN_STARTED", "steps=${attempts.size}")
        }

        for ((index, step) in attempts.withIndex()) {
            val label = "${step.protocolSelection} + beast=${step.beastMode}"
            _uiState.value = _uiState.value.copy(fallbackStepLabel = label, errorMessage = null)
            AzadiEventLogger.log("FALLBACK_ATTEMPT", "step=${index + 1}/$label")

            val trial = original.copy(
                protocolSelection = step.protocolSelection,
                beastModeEnabled = step.beastMode
            )
            settingsStore.save(trial)
            prefs.put(protocolKey, step.protocolSelection)
            prefs.put(beastKey, step.beastMode)

            val before = awaitTunnelState()
            if (before.isRunning) {
                stopTunnel()
                delay(1000)
            }
            if (!startTunnel()) {
                AzadiEventLogger.log("VPN_PERMISSION_DENIED")
                continue
            }
            AzadiEventLogger.log("ANDROID_VPN_SERVICE_STARTED")
            AzadiEventLogger.log("PSIPHON_START_REQUESTED")

            val tunnelUp = waitForConnected(awaitTunnelState, step.timeoutSeconds * 1000, label)
            if (!tunnelUp) {
                AzadiEventLogger.log("FALLBACK_FAILED", "reason=tunnel_timeout label=$label")
                continue
            }

            delay(2500)

            val region = awaitTunnelState().connectionData()?.clientRegion()
            val diag = withContext(Dispatchers.IO) {
                PostConnectDiagnostics.run(step.protocolSelection, region)
            }

            if (diag.internetOk) {
                AzadiEventLogger.log("INTERNET_VERIFY_PASSED", label)
                AzadiEventLogger.log("FALLBACK_SUCCESS", label)
                _uiState.value = _uiState.value.copy(fallbackStepLabel = null)
                return true
            }

            AzadiEventLogger.log("INTERNET_VERIFY_FAILED", label)
            AzadiEventLogger.log("INTERNET_TEST_FAILED", "attempt=${index + 1} label=$label")
            stopTunnel()
            delay(1000)
        }

        settingsStore.save(original)
        prefs.put(protocolKey, original.protocolSelection)
        prefs.put(beastKey, original.beastModeEnabled)
        _uiState.value = _uiState.value.copy(fallbackStepLabel = null)
        AzadiEventLogger.log("FALLBACK_EXHAUSTED")
        AzadiEventLogger.log("INTERNET_VERIFY_EXHAUSTED")
        return false
    }

    private suspend fun waitForConnected(
        awaitTunnelState: suspend () -> TunnelState,
        timeoutMs: Long,
        label: String
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastProgressLog = 0L
        while (System.currentTimeMillis() < deadline) {
            val state = awaitTunnelState()
            val conn = state.connectionData()
            if (state.isRunning && conn?.isConnected == true) return true
            val now = System.currentTimeMillis()
            if (now - lastProgressLog >= 15_000) {
                val phase = when {
                    !state.isRunning -> "service_starting"
                    conn == null -> "no_connection_data"
                    else -> conn.networkConnectionState().name.lowercase()
                }
                AzadiEventLogger.log("TUNNEL_WAIT", "label=$label phase=$phase")
                lastProgressLog = now
            }
            delay(1500)
        }
        return false
    }

    suspend fun runPostConnectDiagnostics(tunnelState: TunnelState) {
        val protocol = settingsStore.load().protocolSelection
        val region = tunnelState.connectionData()?.clientRegion()
        val result = PostConnectDiagnostics.run(protocol, region)
        val ping = PostConnectDiagnostics.measurePingMs()
        _uiState.value = _uiState.value.copy(
            diagnosticsSummary = if (result.internetOk) "Internet OK · ${result.publicIp}" else "Internet check failed",
            pingMs = ping,
            qualityReport = result.quality,
            leakSummary = result.leak.summary
        )
    }

    suspend fun refreshPing() {
        val ping = withContext(Dispatchers.IO) { PostConnectDiagnostics.measurePingMs() }
        _uiState.value = _uiState.value.copy(pingMs = ping)
    }
}
