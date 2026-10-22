package com.psiphon3.ui.azadi

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapHorizontalCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psiphon3.R
import com.psiphon3.MainActivityViewModel
import com.psiphon3.TunnelState
import com.psiphon3.azadi.*
import com.psiphon3.psiphonlibrary.PsiphonConstants
import com.psiphon3.ui.*
import com.psiphon3.ui.theme.AppColors
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun AzadiSettingsScreen(
    viewModel: MainActivityViewModel,
    settingsStore: AzadiSettingsStore,
    settings: AzadiSettings,
    destination: SettingsDestination,
    vpnConnected: Boolean,
    tunnelState: TunnelState,
    connectionUi: ConnectionUiState,
    onDestinationChange: (SettingsDestination) -> Unit,
    onSettingsChanged: (AzadiSettings, ReconnectMode) -> Unit,
    onExportDebug: () -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    var localSettings by remember { mutableStateOf(settings) }
    LaunchedEffect(settings) {
        localSettings = settings
    }

    fun persist(updated: AzadiSettings, reconnect: ReconnectMode = ReconnectMode.HARD, logKey: String = "settings") {
        localSettings = updated
        settingsStore.updateAppSettings(updated, logKey)
        onSettingsChanged(updated, reconnect)

        if (logKey == "protocol") {
            FirebaseAnalyticsManager.logConnectionModeSelected(updated.protocolSelection)
        }
    }

    when (destination) {
        SettingsDestination.LOGS -> { }
        SettingsDestination.BYPASS_IRAN -> BypassIranScreen(
            settings = localSettings,
            onUpdate = { updated, mode -> persist(updated, mode) },
            onBack = { onDestinationChange(SettingsDestination.ROOT) }
        )
        SettingsDestination.SECURE_DNS -> SecureDnsScreen(
            settings = localSettings,
            vpnConnected = vpnConnected,
            onUpdate = { updated, mode -> persist(updated, mode) },
            onBack = { onDestinationChange(SettingsDestination.ROOT) }
        )
        SettingsDestination.PROXY_ONLY -> ProxyOnlyScreen(
            settings = localSettings,
            vpnConnected = vpnConnected,
            onUpdate = { updated, mode -> persist(updated, mode) },
            onBack = { onDestinationChange(SettingsDestination.ROOT) }
        )
        SettingsDestination.SHARE_PROXY -> ShareProxyScreen(
            settings = localSettings,
            vpnConnected = vpnConnected,
            onUpdate = { updated, mode -> persist(updated, mode) },
            onBack = { onDestinationChange(SettingsDestination.ROOT) }
        )
        SettingsDestination.UPSTREAM_PROXY -> UpstreamProxyScreen(
            settings = localSettings,
            onUpdate = { updated, mode -> persist(updated, mode) },
            onBack = { onDestinationChange(SettingsDestination.ROOT) }
        )
        SettingsDestination.ABOUT -> AboutScreen(
            onBack = { onDestinationChange(SettingsDestination.ROOT) },
            onNavigatePrivacy = { onDestinationChange(SettingsDestination.PRIVACY) },
            onNavigateLegal = { onDestinationChange(SettingsDestination.LEGAL) },
            onNavigateSupport = { onDestinationChange(SettingsDestination.SUPPORT) }
        )
        SettingsDestination.LEGAL -> LegalScreen(
            onBack = { onDestinationChange(SettingsDestination.ROOT) },
            onNavigateFullLicense = { onDestinationChange(SettingsDestination.FULL_LICENSE) }
        )
        SettingsDestination.PRIVACY -> PrivacyScreen(onBack = { onDestinationChange(SettingsDestination.ROOT) })
        SettingsDestination.FULL_LICENSE -> FullLicenseNoticesScreen(
            onBack = { onDestinationChange(SettingsDestination.LEGAL) }
        )
        SettingsDestination.SUPPORT -> AzadiSupportScreen(
            onBack = { onDestinationChange(SettingsDestination.ROOT) }
        )
        SettingsDestination.ROOT -> AzadiSettingsRoot(
            settings = localSettings,
            settingsStore = settingsStore,
            vpnConnected = vpnConnected,
            onPersist = { updated, reconnect, logKey -> persist(updated, reconnect, logKey) },
            onNavigate = onDestinationChange,
            onExportDebug = onExportDebug,
            onLanguageSelected = onLanguageSelected
        )
    }
}

/** Rows inside the root's grouped cards run edge to edge; only a little vertical breathing room. */
private val RootGroupPadding = PaddingValues(vertical = 4.dp)

/** Shared container color for the picker sheets (matches the map surface). */
private val SheetContainerColor = Color(0xFF111827)

@Composable
private fun AzadiSettingsRoot(
    settings: AzadiSettings,
    settingsStore: AzadiSettingsStore,
    vpnConnected: Boolean,
    onPersist: (AzadiSettings, ReconnectMode, String) -> Unit,
    onNavigate: (SettingsDestination) -> Unit,
    onExportDebug: () -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var showRegionPicker by remember { mutableStateOf(false) }
    var showProtocolPicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showConduitModePicker by remember { mutableStateOf(false) }
    var showConduitTimeoutPicker by remember { mutableStateOf(false) }
    var showVpnOnDemandModePicker by remember { mutableStateOf(false) }
    var cdnCustomIps by remember(settings.cdnFrontingCustomIpList) { mutableStateOf(settings.cdnFrontingCustomIpList) }
    var cdnCustomSni by remember(settings.cdnFrontingCustomSni) { mutableStateOf(settings.cdnFrontingCustomSni) }

    val availableRegions = remember(showRegionPicker) {
        if (showRegionPicker) com.psiphon3.azadi.PsiphonRegionList.getAvailable(context) else emptyList()
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }.orEmpty()
            val result = ConfigManager.importConfig(context, text)
            if (result.success) {
                Toast.makeText(context, R.string.azadi_config_imported, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, result.error ?: context.getString(R.string.azadi_import_failed), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: context.getString(R.string.azadi_import_failed), Toast.LENGTH_LONG).show()
        }
    }

    val regionCode = settings.egressRegion.ifEmpty { PsiphonConstants.REGION_CODE_ANY }
    val region = ALL_REGIONS.find { it.code == regionCode } ?: ALL_REGIONS.first()
    val regionName = stringResource(region.nameResId)
    val protocolName = protocolDisplayName(settings.protocolSelection)

    AzadiSettingsSurface(contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .testTag("settingsScreen")
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 96.dp)
        ) {
            Text(
                text = stringResource(R.string.azadi_tab_settings),
                color = AppColors.PrimaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                modifier = Modifier
                    .padding(start = 4.dp, end = 4.dp, bottom = 12.dp)
                    .semantics { heading() }
            )

            // Identity hero: icon, wordmark with the peeking lion, version, current setup.
            SettingsHeroCard(
                protocolName = protocolName,
                regionName = regionName,
                protocolIcon = Icons.AutoMirrored.Filled.AltRoute,
                regionIcon = Icons.Filled.Public
            )

            // 1. Connection (read-only)
            AzadiSectionHeader(stringResource(R.string.azadi_connection), icon = Icons.Filled.Bolt)
            AzadiSettingsGroup(contentPadding = RootGroupPadding) {
                AzadiValueRow(
                    label = stringResource(R.string.azadi_config_source),
                    value = configSourceLabel(ConfigManager.configSummary(context)),
                    icon = Icons.Filled.Description,
                    tint = SettingsTints.Gray
                )
                AzadiDivider(SettingsIconInset)
                AzadiValueRow(
                    label = stringResource(R.string.azadi_server_entries),
                    value = ConfigManager.serverEntriesLineCount(context).toString(),
                    icon = Icons.Filled.Storage,
                    tint = SettingsTints.Teal,
                    monospaceValue = true
                )
                AzadiDivider()
                AzadiFooterNote(ConfigManager.embeddedEntriesSubtitle(context))
            }

            // 2. Region
            AzadiSectionHeader(stringResource(R.string.dashboard_region), icon = Icons.Filled.Place)
            AzadiSettingsGroup(contentPadding = RootGroupPadding) {
                AzadiPickerRow(
                    label = stringResource(R.string.azadi_egress_region),
                    value = regionName,
                    onClick = { showRegionPicker = true },
                    icon = Icons.Filled.Public,
                    tint = SettingsTints.Blue
                )
                AzadiDivider()
                AzadiFooterNote(stringResource(R.string.azadi_region_hint))
            }

            // 3. Transport
            AzadiSectionHeader(stringResource(R.string.azadi_transport), icon = Icons.Filled.Route)
            AzadiSettingsGroup(contentPadding = RootGroupPadding) {
                AzadiPickerRow(
                    label = stringResource(R.string.azadi_protocol_label),
                    value = protocolName,
                    onClick = { showProtocolPicker = true },
                    icon = Icons.AutoMirrored.Filled.AltRoute,
                    tint = AppColors.IranGreen
                )
                AzadiDivider(SettingsIconInset)
                AzadiToggleRow(
                    label = stringResource(R.string.beastModePreferenceTitle),
                    checked = settings.beastModeEnabled,
                    onCheckedChange = { onPersist(settings.copy(beastModeEnabled = it), ReconnectMode.SOFT, "beastMode") },
                    testTag = "beastModeToggle",
                    icon = Icons.Filled.Bolt,
                    tint = SettingsTints.Orange
                )
                if (settings.protocolSelection == "conduit" && settingsStore.conduitConnectAllowed()) {
                    AzadiDivider(SettingsIconInset)
                    AzadiPickerRow(
                        label = stringResource(R.string.azadi_conduit_mode),
                        value = conduitModeDisplayName(settings.conduitMode),
                        onClick = { showConduitModePicker = true },
                        icon = Icons.Filled.Hub,
                        tint = SettingsTints.Purple
                    )
                    AzadiDivider(SettingsIconInset)
                    AzadiToggleRow(
                        label = stringResource(R.string.azadi_reject_censored_countries),
                        checked = settings.rejectCensoredCountryProxies,
                        onCheckedChange = {
                            onPersist(settings.copy(rejectCensoredCountryProxies = it), ReconnectMode.SOFT, "conduit_reject_countries")
                        },
                        icon = Icons.Filled.PanTool,
                        tint = SettingsTints.Red
                    )
                    AzadiDivider(SettingsIconInset)
                    AzadiPickerRow(
                        label = stringResource(R.string.azadi_conduit_timeout),
                        value = conduitTimeoutLabel(settings.conduitTimeoutSeconds),
                        onClick = { showConduitTimeoutPicker = true },
                        icon = Icons.Filled.Timer,
                        tint = SettingsTints.Teal
                    )
                }
                AzadiDivider()
                AzadiFooterNote(transportHint(settings))
                if (!settingsStore.conduitConnectAllowed()) {
                    AzadiWarningBlock(stringResource(R.string.azadi_conduit_blocked))
                }
            }

            if (settings.protocolSelection == "cdn_fronting") {
                AzadiSectionHeader(stringResource(R.string.azadi_cdn_fronting_section), icon = Icons.Filled.Cloud)
                AzadiSettingsGroup(contentPadding = RootGroupPadding) {
                    AzadiToggleRow(
                        label = stringResource(R.string.azadi_cdn_builtin_scan),
                        checked = settings.cdnFrontingUseBuiltInScan,
                        onCheckedChange = {
                            onPersist(settings.copy(cdnFrontingUseBuiltInScan = it), ReconnectMode.SOFT, "cdnBuiltInScan")
                        },
                        icon = Icons.Filled.Radar,
                        tint = SettingsTints.Indigo
                    )
                    AzadiDivider()
                    OutlinedTextField(
                        value = cdnCustomIps,
                        onValueChange = { cdnCustomIps = it },
                        label = { Text(stringResource(R.string.azadi_cdn_custom_ips)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                        minLines = 2,
                        colors = settingsTextFieldColors()
                    )
                    OutlinedTextField(
                        value = cdnCustomSni,
                        onValueChange = { cdnCustomSni = it },
                        label = { Text(stringResource(R.string.azadi_cdn_custom_sni)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        minLines = 2,
                        colors = settingsTextFieldColors()
                    )
                    TextButton(
                        onClick = {
                            onPersist(
                                settings.copy(
                                    cdnFrontingCustomIpList = cdnCustomIps.trim(),
                                    cdnFrontingCustomSni = cdnCustomSni.trim()
                                ),
                                ReconnectMode.SOFT,
                                "cdnCustomOverrides"
                            )
                        },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            stringResource(R.string.azadi_save_cdn_overrides),
                            color = AppColors.IranGreenBright,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    AzadiFooterNote(cdnSummaryNote(cdnCustomIps, cdnCustomSni, settings.cdnFrontingUseBuiltInScan))
                }
            }

            // 4. Upstream proxy
            AzadiSectionHeader(stringResource(R.string.settings2_upstream_proxy), icon = Icons.Filled.SwapHoriz)
            AzadiSettingsGroup(contentPadding = RootGroupPadding) {
                AzadiToggleRow(
                    label = stringResource(R.string.azadi_upstream_proxy_enable),
                    checked = settings.upstreamProxyEnabled,
                    onCheckedChange = { onPersist(settings.copy(upstreamProxyEnabled = it), ReconnectMode.SOFT, "upstreamProxy") },
                    icon = Icons.Filled.Lan,
                    tint = SettingsTints.Blue
                )
                if (settings.upstreamProxyEnabled) {
                    AzadiDivider(SettingsIconInset)
                    AzadiNavRow(
                        title = stringResource(R.string.label_proxy_settings),
                        subtitle = proxySubtitle(settings),
                        onClick = { onNavigate(SettingsDestination.UPSTREAM_PROXY) },
                        icon = Icons.Filled.Tune,
                        tint = SettingsTints.Gray
                    )
                }
            }

            // 5. Proxy Only
            AzadiSectionHeader(stringResource(R.string.azadi_proxy_only_mode_title), icon = Icons.Filled.Layers)
            AzadiSettingsGroup(contentPadding = RootGroupPadding) {
                AzadiToggleRow(
                    label = stringResource(R.string.azadi_proxy_only_mode_title),
                    checked = settings.proxyOnlyModeEnabled,
                    onCheckedChange = { onPersist(settings.copy(proxyOnlyModeEnabled = it), ReconnectMode.SOFT, "proxyOnly") },
                    subtitle = stringResource(R.string.azadi_proxy_only_subtitle),
                    warning = stringResource(R.string.azadi_proxy_only_warning),
                    testTag = "proxyOnlySettingsToggle",
                    icon = Icons.Filled.SwapHorizontalCircle,
                    tint = SettingsTints.Orange
                )
                AzadiDivider(SettingsIconInset)
                AzadiNavRow(
                    title = stringResource(R.string.azadi_proxy_only_mode_title),
                    subtitle = stringResource(R.string.azadi_proxy_only_nav_hint),
                    onClick = { onNavigate(SettingsDestination.PROXY_ONLY) },
                    testTag = "proxyOnlySettingsLink",
                    icon = Icons.Filled.Apps,
                    tint = SettingsTints.Orange
                )
            }

            // 6–8. Share / Bypass / Secure DNS
            AzadiSectionHeader(stringResource(R.string.settings2_network_tools), icon = Icons.Filled.Shield)
            AzadiSettingsGroup(contentPadding = RootGroupPadding) {
                AzadiNavRow(
                    title = stringResource(R.string.azadi_share_proxy_title),
                    subtitle = stringResource(R.string.azadi_share_proxy_subtitle),
                    onClick = { onNavigate(SettingsDestination.SHARE_PROXY) },
                    testTag = "shareProxyRow",
                    icon = Icons.Filled.WifiTethering,
                    tint = AppColors.IranGreen
                )
                AzadiDivider(SettingsIconInset)
                AzadiNavRow(
                    title = stringResource(R.string.azadi_bypass_iran),
                    subtitle = stringResource(R.string.azadi_bypass_iran_subtitle),
                    onClick = { onNavigate(SettingsDestination.BYPASS_IRAN) },
                    testTag = "bypassIranRow",
                    icon = Icons.Filled.Directions,
                    tint = SettingsTints.Teal
                )
                AzadiDivider(SettingsIconInset)
                AzadiNavRow(
                    title = stringResource(R.string.azadi_secure_dns),
                    subtitle = stringResource(R.string.azadi_secure_dns_subtitle),
                    onClick = { onNavigate(SettingsDestination.SECURE_DNS) },
                    testTag = "secureDnsRow",
                    icon = Icons.Filled.Security,
                    tint = SettingsTints.Indigo
                )
            }

            // Behavior
            AzadiSectionHeader(stringResource(R.string.azadi_behavior), icon = Icons.Filled.Tune)
            AzadiSettingsGroup(contentPadding = RootGroupPadding) {
                AzadiToggleRow(
                    label = stringResource(R.string.azadi_smart_fallback),
                    checked = settings.smartFallbackChainEnabled,
                    onCheckedChange = { onPersist(settings.copy(smartFallbackChainEnabled = it), ReconnectMode.SOFT, "smartFallback") },
                    icon = Icons.Filled.Autorenew,
                    tint = AppColors.IranGreen
                )
                AzadiDivider(SettingsIconInset)
                AzadiToggleRow(
                    label = stringResource(R.string.azadi_auto_reconnect),
                    checked = settings.autoReconnect,
                    onCheckedChange = { onPersist(settings.copy(autoReconnect = it), ReconnectMode.NONE, "autoReconnect") },
                    icon = Icons.Filled.Link,
                    tint = SettingsTints.Teal
                )
                AzadiDivider(SettingsIconInset)
                AzadiToggleRow(
                    label = stringResource(R.string.azadi_connect_on_launch),
                    checked = settings.connectOnLaunch,
                    onCheckedChange = { onPersist(settings.copy(connectOnLaunch = it), ReconnectMode.NONE, "connectOnLaunch") },
                    icon = Icons.Filled.PowerSettingsNew,
                    tint = AppColors.IranRed
                )
                AzadiDivider(SettingsIconInset)
                AzadiToggleRow(
                    label = stringResource(R.string.azadi_vpn_on_demand),
                    checked = settings.vpnOnDemandEnabled,
                    onCheckedChange = { enabled ->
                        onPersist(settings.copy(vpnOnDemandEnabled = enabled), ReconnectMode.NONE, "vpnOnDemand")
                        if (enabled) context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                    },
                    testTag = "vpnOnDemandToggle",
                    icon = Icons.Filled.OfflineBolt,
                    tint = SettingsTints.Purple
                )
                if (settings.vpnOnDemandEnabled) {
                    AzadiDivider(SettingsIconInset)
                    AzadiPickerRow(
                        label = stringResource(R.string.azadi_vpn_on_demand_mode),
                        value = vpnOnDemandModeLabel(settings.vpnOnDemandMode),
                        onClick = { showVpnOnDemandModePicker = true },
                        icon = Icons.Filled.Wifi,
                        tint = SettingsTints.Blue
                    )
                }
                AzadiDivider(SettingsIconInset)
                AzadiPickerRow(
                    label = stringResource(R.string.azadi_language),
                    value = languageDisplayName(settings.preferredLanguage),
                    onClick = { showLanguagePicker = true },
                    icon = Icons.Filled.Translate,
                    tint = SettingsTints.Pink
                )
            }

            // Advanced
            AzadiSectionHeader(stringResource(R.string.azadi_advanced_developer), icon = Icons.Filled.Build)
            AzadiSettingsGroup(contentPadding = RootGroupPadding) {
                AzadiFooterNote(stringResource(R.string.azadi_advanced_hint))
                AzadiDivider()
                AzadiActionRow(
                    title = stringResource(R.string.azadi_retry_bundled),
                    icon = Icons.Filled.CloudDownload,
                    tint = SettingsTints.Blue,
                    testTag = "retry_bundled_install_button",
                    onClick = {
                        val ok = ConfigManager.retryBundledInstall(context)
                        Toast.makeText(
                            context,
                            if (ok) R.string.azadi_bundled_install_ok else R.string.azadi_no_config,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
                AzadiDivider(SettingsIconInset)
                AzadiActionRow(
                    title = stringResource(R.string.azadi_import_config_json),
                    icon = Icons.Filled.SaveAlt,
                    tint = SettingsTints.Indigo,
                    testTag = "import_config_button",
                    onClick = {
                        try {
                            importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        } catch (e: Exception) {
                            Toast.makeText(context, R.string.azadi_import_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                AzadiDivider(SettingsIconInset)
                AzadiActionRow(
                    title = stringResource(R.string.azadi_export_debug),
                    icon = Icons.Filled.BugReport,
                    tint = SettingsTints.Orange,
                    testTag = "export_debug_report_button",
                    onClick = { onExportDebug() }
                )
                AzadiDivider(SettingsIconInset)
                AzadiActionRow(
                    title = stringResource(R.string.azadi_reset_defaults),
                    icon = Icons.Filled.RestartAlt,
                    tint = AppColors.IranRed,
                    titleColor = AzadiDestructiveRed,
                    testTag = "reset_settings_defaults_button",
                    onClick = { showResetDialog = true }
                )
            }

            // Logs
            AzadiSectionHeader(stringResource(R.string.logs_tab_name), icon = Icons.AutoMirrored.Filled.ManageSearch)
            AzadiSettingsGroup(contentPadding = RootGroupPadding) {
                AzadiNavRow(
                    title = stringResource(R.string.logs_tab_name),
                    onClick = { onNavigate(SettingsDestination.LOGS) },
                    testTag = "logsSettingsLink",
                    icon = Icons.AutoMirrored.Filled.Article,
                    tint = SettingsTints.Gray
                )
            }

            // Legal
            AzadiSectionHeader(stringResource(R.string.azadi_legal), icon = Icons.Filled.Verified)
            AzadiSettingsGroup(contentPadding = RootGroupPadding) {
                AzadiNavRow(
                    title = stringResource(R.string.azadi_legal_opensource),
                    onClick = { onNavigate(SettingsDestination.LEGAL) },
                    testTag = "legalOpenSourceLink",
                    icon = Icons.Filled.Code,
                    tint = SettingsTints.Gray
                )
                AzadiDivider(SettingsIconInset)
                AzadiNavRow(
                    title = stringResource(R.string.azadi_privacy_notice),
                    onClick = { onNavigate(SettingsDestination.PRIVACY) },
                    testTag = "privacyNoticeLink",
                    icon = Icons.Filled.PanTool,
                    tint = SettingsTints.Blue
                )
                AzadiDivider(SettingsIconInset)
                AzadiNavRow(
                    title = stringResource(R.string.azadi_about_app),
                    onClick = { onNavigate(SettingsDestination.ABOUT) },
                    icon = Icons.Filled.Info,
                    tint = SettingsTints.Teal
                )
                AzadiDivider(SettingsIconInset)
                AzadiNavRow(
                    title = stringResource(R.string.azadi_support_app),
                    onClick = { onNavigate(SettingsDestination.SUPPORT) },
                    testTag = "supportAzadiTunnelLink",
                    icon = Icons.Filled.Favorite,
                    tint = AppColors.IranRed
                )
            }
        }
    }

    if (showRegionPicker) {
        RegionPickerSheet(
            selectedRegionCode = settings.egressRegion.ifEmpty { PsiphonConstants.REGION_CODE_ANY },
            onRegionSelected = { onPersist(settings.copy(egressRegion = it), ReconnectMode.SOFT, "egressRegion") },
            onDismiss = { showRegionPicker = false },
            regionCodes = availableRegions
        )
    }
    if (showProtocolPicker) {
        ProtocolPickerSheet(
            current = settings.protocolSelection,
            conduitAllowed = settingsStore.conduitConnectAllowed(),
            onSelect = { onPersist(settings.copy(protocolSelection = it), ReconnectMode.SOFT, "protocolSelection") },
            onDismiss = { showProtocolPicker = false }
        )
    }
    if (showLanguagePicker) {
        LanguagePickerSheet(
            current = settings.preferredLanguage,
            onSelect = { code ->
                showLanguagePicker = false
                onLanguageSelected(code)
            },
            onDismiss = { showLanguagePicker = false }
        )
    }
    if (showConduitModePicker) {
        ConduitModePickerSheet(
            current = settings.conduitMode,
            onSelect = {
                onPersist(settings.copy(conduitMode = it), ReconnectMode.SOFT, "conduitMode")
                showConduitModePicker = false
            },
            onDismiss = { showConduitModePicker = false }
        )
    }
    if (showConduitTimeoutPicker) {
        ConduitTimeoutPickerSheet(
            current = settings.conduitTimeoutSeconds,
            onSelect = {
                onPersist(settings.copy(conduitTimeoutSeconds = it), ReconnectMode.SOFT, "conduitTimeout")
                showConduitTimeoutPicker = false
            },
            onDismiss = { showConduitTimeoutPicker = false }
        )
    }
    if (showVpnOnDemandModePicker) {
        VpnOnDemandModePickerSheet(
            current = settings.vpnOnDemandMode,
            onSelect = {
                onPersist(settings.copy(vpnOnDemandMode = it), ReconnectMode.SOFT, "vpnOnDemandMode")
                showVpnOnDemandModePicker = false
            },
            onDismiss = { showVpnOnDemandModePicker = false }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = SheetContainerColor,
            title = { Text(stringResource(R.string.azadi_reset_defaults)) },
            text = { Text(stringResource(R.string.azadi_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onPersist(settingsStore.resetToDefaults(settings), ReconnectMode.HARD, "resetDefaults")
                    showResetDialog = false
                }) { Text(stringResource(R.string.lbl_yes), color = AzadiDestructiveRed) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.lbl_no), color = AppColors.PrimaryText)
                }
            }
        )
    }
}

@Composable
private fun settingsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AppColors.PrimaryText,
    unfocusedTextColor = AppColors.PrimaryText,
    focusedBorderColor = AppColors.IranGreenBright,
    unfocusedBorderColor = AppColors.GlassStroke,
    focusedLabelColor = AppColors.IranGreenBright,
    unfocusedLabelColor = AppColors.SecondaryText,
    cursorColor = AppColors.IranGreenBright
)

/** Localizes the English summary returned by [ConfigManager.configSummary]. */
@Composable
private fun configSourceLabel(summary: String): String = when (summary) {
    "Bundled" -> stringResource(R.string.azadi_bundled)
    "Custom" -> stringResource(R.string.settings2_custom)
    "No config" -> stringResource(R.string.azadi_no_config)
    else -> summary
}

@Composable
private fun SheetOption(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> AppColors.SecondaryText.copy(alpha = 0.6f)
                selected -> AppColors.IranGreenBright
                else -> AppColors.PrimaryText
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtocolPickerSheet(
    current: String,
    conduitAllowed: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf("auto", "direct", "cdn_fronting", "conduit")
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetContainerColor) {
        options.forEach { protocol ->
            val enabled = protocol != "conduit" || conduitAllowed
            SheetOption(
                text = protocolDisplayName(protocol),
                selected = protocol == current,
                enabled = enabled,
                onClick = { if (enabled) { onSelect(protocol); onDismiss() } }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerSheet(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "system" to stringResource(R.string.azadi_lang_system),
        "en" to stringResource(R.string.azadi_lang_english),
        "fa" to stringResource(R.string.azadi_lang_persian)
    )
    val selectedCode = if (current == "en" || current == "fa") current else "system"
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetContainerColor) {
        options.forEach { (code, label) ->
            SheetOption(
                text = label,
                selected = code == selectedCode,
                onClick = { onSelect(code); onDismiss() }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun protocolDisplayName(protocol: String): String = when (protocol) {
    "auto" -> stringResource(R.string.azadi_protocol_auto)
    "direct" -> stringResource(R.string.azadi_protocol_direct)
    "cdn_fronting" -> stringResource(R.string.azadi_protocol_cdn)
    "conduit" -> stringResource(R.string.azadi_protocol_conduit)
    else -> protocol.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun languageDisplayName(code: String): String = when (code) {
    "en" -> stringResource(R.string.azadi_lang_english)
    "fa" -> stringResource(R.string.azadi_lang_persian)
    else -> stringResource(R.string.azadi_lang_system)
}

@Composable
private fun transportHint(settings: AzadiSettings): String = when {
    settings.beastModeEnabled && settings.protocolSelection == "auto" ->
        stringResource(R.string.azadi_transport_hint_beast_auto)
    settings.protocolSelection == "direct" -> stringResource(R.string.azadi_transport_hint_direct)
    settings.protocolSelection == "cdn_fronting" -> stringResource(R.string.azadi_transport_hint_cdn)
    settings.protocolSelection == "conduit" -> stringResource(R.string.azadi_transport_hint_conduit)
    else -> stringResource(R.string.azadi_transport_hint_auto)
}

@Composable
private fun conduitModeDisplayName(mode: String): String = when (mode) {
    "auto" -> stringResource(R.string.azadi_conduit_mode_auto)
    "azaditunnel" -> stringResource(R.string.azadi_conduit_mode_community)
    "public" -> stringResource(R.string.azadi_conduit_mode_public)
    else -> mode
}

@Composable
private fun conduitTimeoutLabel(seconds: Int): String = when (seconds) {
    120 -> stringResource(R.string.azadi_conduit_timeout_2m)
    180 -> stringResource(R.string.azadi_conduit_timeout_3m)
    300 -> stringResource(R.string.azadi_conduit_timeout_5m)
    600 -> stringResource(R.string.azadi_conduit_timeout_10m)
    else -> "${seconds / 60} min"
}

@Composable
private fun vpnOnDemandModeLabel(mode: String): String = when (mode) {
    "wifi" -> stringResource(R.string.azadi_vpn_on_demand_wifi)
    "cellular" -> stringResource(R.string.azadi_vpn_on_demand_cellular)
    else -> stringResource(R.string.azadi_vpn_on_demand_always)
}

@Composable
private fun cdnSummaryNote(customIps: String, customSni: String, builtIn: Boolean): String {
    val ipCount = customIps.lines().count { it.isNotBlank() }
    val sniCount = customSni.lines().count { it.isNotBlank() }
    return stringResource(
        R.string.azadi_cdn_summary,
        if (builtIn) stringResource(R.string.azadi_yes) else stringResource(R.string.azadi_no),
        ipCount,
        sniCount
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConduitModePickerSheet(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf("auto", "azaditunnel", "public")
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetContainerColor) {
        options.forEach { mode ->
            SheetOption(
                text = conduitModeDisplayName(mode),
                selected = mode == current,
                onClick = { onSelect(mode) }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConduitTimeoutPickerSheet(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(120, 180, 300, 600)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetContainerColor) {
        options.forEach { seconds ->
            SheetOption(
                text = conduitTimeoutLabel(seconds),
                selected = seconds == current,
                onClick = { onSelect(seconds) }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VpnOnDemandModePickerSheet(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf("always", "wifi", "cellular")
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetContainerColor) {
        options.forEach { mode ->
            SheetOption(
                text = vpnOnDemandModeLabel(mode),
                selected = mode == current,
                onClick = { onSelect(mode) }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun proxySubtitle(settings: AzadiSettings): String {
    if (settings.upstreamProxyUseSystem) return stringResource(R.string.azadi_upstream_system_proxy)
    if (settings.upstreamProxyHost.isNotBlank()) {
        return "${settings.upstreamProxyHost}:${settings.upstreamProxyPort}"
    }
    return stringResource(R.string.azadi_upstream_configure)
}
