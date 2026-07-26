package com.psiphon3.ui.azadi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
    onSettingsChanged: (AzadiSettings, Boolean) -> Unit,
    onExportDebug: () -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    var localSettings by remember { mutableStateOf(settings) }
    LaunchedEffect(settings) {
        localSettings = settings
    }

    fun persist(updated: AzadiSettings, reconnect: Boolean = true, logKey: String = "settings") {
        localSettings = updated
        settingsStore.updateAppSettings(updated, logKey)
        onSettingsChanged(updated, reconnect)
    }

    when (destination) {
        SettingsDestination.LOGS -> { }
        SettingsDestination.BYPASS_IRAN -> BypassIranScreen(
            settings = localSettings,
            onUpdate = { persist(it) },
            onBack = { onDestinationChange(SettingsDestination.ROOT) }
        )
        SettingsDestination.SECURE_DNS -> SecureDnsScreen(
            settings = localSettings,
            vpnConnected = vpnConnected,
            onUpdate = { persist(it, reconnect = true) },
            onBack = { onDestinationChange(SettingsDestination.ROOT) }
        )
        SettingsDestination.PROXY_ONLY -> ProxyOnlyScreen(
            settings = localSettings,
            vpnConnected = vpnConnected,
            onUpdate = { persist(it, reconnect = true) },
            onBack = { onDestinationChange(SettingsDestination.ROOT) }
        )
        SettingsDestination.SHARE_PROXY -> ShareProxyScreen(
            settings = localSettings,
            vpnConnected = vpnConnected,
            onUpdate = { persist(it, reconnect = true) },
            onBack = { onDestinationChange(SettingsDestination.ROOT) }
        )
        SettingsDestination.UPSTREAM_PROXY -> UpstreamProxyScreen(
            settings = localSettings,
            onUpdate = { persist(it, reconnect = true) },
            onBack = { onDestinationChange(SettingsDestination.ROOT) }
        )
        SettingsDestination.ABOUT -> AboutScreen(
            onBack = { onDestinationChange(SettingsDestination.ROOT) },
            onNavigatePrivacy = { onDestinationChange(SettingsDestination.PRIVACY) },
            onNavigateLegal = { onDestinationChange(SettingsDestination.LEGAL) }
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

@Composable
private fun AzadiSettingsRoot(
    settings: AzadiSettings,
    settingsStore: AzadiSettingsStore,
    vpnConnected: Boolean,
    onPersist: (AzadiSettings, Boolean, String) -> Unit,
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

    Column(
        modifier = Modifier
            .testTag("settingsScreen")
            .fillMaxSize()
            .background(AppColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.azadi_tab_settings),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )

        // 1. Connection (read-only)
        AzadiSectionHeader(stringResource(R.string.azadi_connection))
        AzadiSettingsGroup {
            AzadiValueRow(
                label = stringResource(R.string.azadi_config_source),
                value = ConfigManager.configSummary(context)
            )
            AzadiDivider()
            AzadiValueRow(
                label = stringResource(R.string.azadi_server_entries),
                value = ConfigManager.serverEntriesLineCount(context).toString()
            )
            AzadiDivider()
            AzadiFooterNote(ConfigManager.embeddedEntriesSubtitle(context))
        }

        // Region
        AzadiSectionHeader(stringResource(R.string.dashboard_region))
        AzadiSettingsGroup {
            val regionCode = settings.egressRegion.ifEmpty { PsiphonConstants.REGION_CODE_ANY }
            val region = ALL_REGIONS.find { it.code == regionCode } ?: ALL_REGIONS.first()
            AzadiPickerRow(
                label = stringResource(R.string.azadi_egress_region),
                value = stringResource(region.nameResId),
                onClick = { showRegionPicker = true }
            )
            AzadiDivider()
            AzadiFooterNote(stringResource(R.string.azadi_region_hint))
        }

        // 3. Transport
        AzadiSectionHeader(stringResource(R.string.azadi_transport))
        AzadiSettingsGroup {
            AzadiPickerRow(
                label = stringResource(R.string.azadi_protocol_label),
                value = protocolDisplayName(settings.protocolSelection),
                onClick = { showProtocolPicker = true }
            )
            AzadiDivider()
            AzadiToggleRow(
                label = stringResource(R.string.beastModePreferenceTitle),
                checked = settings.beastModeEnabled,
                onCheckedChange = { onPersist(settings.copy(beastModeEnabled = it), false, "beastMode") },
                testTag = "beastModeToggle"
            )
            if (settings.protocolSelection == "conduit" && settingsStore.conduitConnectAllowed()) {
                AzadiDivider()
                AzadiPickerRow(
                    label = stringResource(R.string.azadi_conduit_mode),
                    value = conduitModeDisplayName(settings.conduitMode),
                    onClick = { showConduitModePicker = true }
                )
                AzadiDivider()
                AzadiToggleRow(
                    label = stringResource(R.string.azadi_reject_censored_countries),
                    checked = settings.rejectCensoredCountryProxies,
                    onCheckedChange = {
                        onPersist(settings.copy(rejectCensoredCountryProxies = it), false, "conduit_reject_countries")
                    }
                )
                AzadiDivider()
                AzadiPickerRow(
                    label = stringResource(R.string.azadi_conduit_timeout),
                    value = conduitTimeoutLabel(settings.conduitTimeoutSeconds),
                    onClick = { showConduitTimeoutPicker = true }
                )
            }
            AzadiDivider()
            AzadiFooterNote(transportHint(settings))
            if (!settingsStore.conduitConnectAllowed()) {
                AzadiWarningBlock(stringResource(R.string.azadi_conduit_blocked))
            }
        }

        if (settings.protocolSelection == "cdn_fronting") {
            AzadiSectionHeader(stringResource(R.string.azadi_cdn_fronting_section))
            AzadiSettingsGroup {
                AzadiToggleRow(
                    label = stringResource(R.string.azadi_cdn_builtin_scan),
                    checked = settings.cdnFrontingUseBuiltInScan,
                    onCheckedChange = {
                        onPersist(settings.copy(cdnFrontingUseBuiltInScan = it), false, "cdnBuiltInScan")
                    }
                )
                AzadiDivider()
                OutlinedTextField(
                    value = cdnCustomIps,
                    onValueChange = { cdnCustomIps = it },
                    label = { Text(stringResource(R.string.azadi_cdn_custom_ips)) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    minLines = 2
                )
                OutlinedTextField(
                    value = cdnCustomSni,
                    onValueChange = { cdnCustomSni = it },
                    label = { Text(stringResource(R.string.azadi_cdn_custom_sni)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    minLines = 2
                )
                TextButton(
                    onClick = {
                        onPersist(
                            settings.copy(
                                cdnFrontingCustomIpList = cdnCustomIps.trim(),
                                cdnFrontingCustomSni = cdnCustomSni.trim()
                            ),
                            false,
                            "cdnCustomOverrides"
                        )
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) { Text(stringResource(R.string.azadi_save_cdn_overrides)) }
                AzadiFooterNote(cdnSummaryNote(cdnCustomIps, cdnCustomSni, settings.cdnFrontingUseBuiltInScan))
            }
        }

        // 4. Upstream proxy
        AzadiSectionHeader(stringResource(R.string.label_proxy_settings))
        AzadiSettingsGroup {
            AzadiToggleRow(
                label = stringResource(R.string.azadi_upstream_proxy_enable),
                checked = settings.upstreamProxyEnabled,
                onCheckedChange = { onPersist(settings.copy(upstreamProxyEnabled = it), false, "upstreamProxy") }
            )
            if (settings.upstreamProxyEnabled) {
                AzadiDivider()
                AzadiNavRow(
                    title = stringResource(R.string.label_proxy_settings),
                    subtitle = proxySubtitle(settings),
                    onClick = { onNavigate(SettingsDestination.UPSTREAM_PROXY) }
                )
            }
        }

        // 5. Proxy Only
        AzadiSectionHeader(stringResource(R.string.azadi_proxy_only_mode_title))
        AzadiSettingsGroup {
            AzadiToggleRow(
                label = stringResource(R.string.azadi_proxy_only_mode_title),
                checked = settings.proxyOnlyModeEnabled,
                onCheckedChange = { onPersist(settings.copy(proxyOnlyModeEnabled = it), true, "proxyOnly") },
                subtitle = stringResource(R.string.azadi_proxy_only_subtitle),
                warning = stringResource(R.string.azadi_proxy_only_warning),
                testTag = "proxyOnlySettingsToggle"
            )
            AzadiDivider()
            AzadiNavRow(
                title = stringResource(R.string.azadi_proxy_only_mode_title),
                subtitle = stringResource(R.string.azadi_proxy_only_nav_hint),
                onClick = { onNavigate(SettingsDestination.PROXY_ONLY) },
                testTag = "proxyOnlySettingsLink"
            )
        }

        // 6–8. Share / Bypass / Secure DNS
        AzadiSectionHeader(stringResource(R.string.azadi_network_privacy))
        AzadiSettingsGroup {
            AzadiNavRow(
                title = stringResource(R.string.azadi_share_proxy_title),
                subtitle = stringResource(R.string.azadi_share_proxy_subtitle),
                onClick = { onNavigate(SettingsDestination.SHARE_PROXY) },
                testTag = "shareProxyRow"
            )
            AzadiDivider()
            AzadiNavRow(
                title = stringResource(R.string.azadi_bypass_iran),
                subtitle = stringResource(R.string.azadi_bypass_iran_subtitle),
                onClick = { onNavigate(SettingsDestination.BYPASS_IRAN) },
                testTag = "bypassIranRow"
            )
            AzadiDivider()
            AzadiNavRow(
                title = stringResource(R.string.azadi_secure_dns),
                subtitle = stringResource(R.string.azadi_secure_dns_subtitle),
                onClick = { onNavigate(SettingsDestination.SECURE_DNS) },
                testTag = "secureDnsRow"
            )
        }

        // Behavior
        AzadiSectionHeader(stringResource(R.string.azadi_behavior))
        AzadiSettingsGroup {
            AzadiToggleRow(
                label = stringResource(R.string.azadi_smart_fallback),
                checked = settings.smartFallbackChainEnabled,
                onCheckedChange = { onPersist(settings.copy(smartFallbackChainEnabled = it), false, "smartFallback") }
            )
            AzadiDivider()
            AzadiToggleRow(
                label = stringResource(R.string.azadi_auto_reconnect),
                checked = settings.autoReconnect,
                onCheckedChange = { onPersist(settings.copy(autoReconnect = it), false, "autoReconnect") }
            )
            AzadiDivider()
            AzadiToggleRow(
                label = stringResource(R.string.azadi_connect_on_launch),
                checked = settings.connectOnLaunch,
                onCheckedChange = { onPersist(settings.copy(connectOnLaunch = it), false, "connectOnLaunch") }
            )
            AzadiDivider()
            AzadiToggleRow(
                label = stringResource(R.string.azadi_vpn_on_demand),
                checked = settings.vpnOnDemandEnabled,
                onCheckedChange = { enabled ->
                    onPersist(settings.copy(vpnOnDemandEnabled = enabled), false, "vpnOnDemand")
                    if (enabled) context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                },
                testTag = "vpnOnDemandToggle"
            )
            if (settings.vpnOnDemandEnabled) {
                AzadiDivider()
                AzadiPickerRow(
                    label = stringResource(R.string.azadi_vpn_on_demand_mode),
                    value = vpnOnDemandModeLabel(settings.vpnOnDemandMode),
                    onClick = { showVpnOnDemandModePicker = true }
                )
            }
            AzadiDivider()
            AzadiPickerRow(
                label = stringResource(R.string.azadi_language),
                value = languageDisplayName(settings.preferredLanguage),
                onClick = { showLanguagePicker = true }
            )
        }

        // Advanced
        AzadiSectionHeader(stringResource(R.string.azadi_advanced_developer))
        AzadiSettingsGroup {
            AzadiFooterNote(stringResource(R.string.azadi_advanced_hint))
            AzadiActionLink(
                text = stringResource(R.string.azadi_retry_bundled),
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
            AzadiDivider()
            AzadiActionLink(
                text = stringResource(R.string.azadi_import_config_json),
                testTag = "import_config_button",
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }
            )
            AzadiDivider()
            AzadiActionLink(
                text = stringResource(R.string.azadi_export_debug),
                testTag = "export_debug_report_button",
                onClick = { onExportDebug() }
            )
            AzadiDivider()
            AzadiActionLink(
                text = stringResource(R.string.azadi_reset_defaults),
                color = AzadiDestructiveRed,
                testTag = "reset_settings_defaults_button",
                onClick = { showResetDialog = true }
            )
        }

        // Logs
        AzadiSectionHeader(stringResource(R.string.logs_tab_name))
        AzadiSettingsGroup {
            AzadiNavRow(
                title = stringResource(R.string.logs_tab_name),
                onClick = { onNavigate(SettingsDestination.LOGS) },
                testTag = "logsSettingsLink"
            )
        }

        // Legal
        AzadiSectionHeader(stringResource(R.string.azadi_legal))
        AzadiSettingsGroup {
            AzadiNavRow(
                title = stringResource(R.string.azadi_legal_opensource),
                onClick = { onNavigate(SettingsDestination.LEGAL) },
                testTag = "legalOpenSourceLink"
            )
            AzadiDivider()
            AzadiNavRow(
                title = stringResource(R.string.azadi_privacy_notice),
                onClick = { onNavigate(SettingsDestination.PRIVACY) },
                testTag = "privacyNoticeLink"
            )
            AzadiDivider()
            AzadiNavRow(
                title = stringResource(R.string.azadi_about_app),
                onClick = { onNavigate(SettingsDestination.ABOUT) }
            )
        }

        Spacer(Modifier.height(76.dp))
    }

    if (showRegionPicker) {
        RegionPickerSheet(
            selectedRegionCode = settings.egressRegion.ifEmpty { PsiphonConstants.REGION_CODE_ANY },
            onRegionSelected = { onPersist(settings.copy(egressRegion = it), false, "egressRegion") },
            onDismiss = { showRegionPicker = false },
            regionCodes = PsiphonRegionList.all
        )
    }
    if (showProtocolPicker) {
        ProtocolPickerSheet(
            current = settings.protocolSelection,
            conduitAllowed = settingsStore.conduitConnectAllowed(),
            onSelect = { onPersist(settings.copy(protocolSelection = it), false, "protocolSelection") },
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
                onPersist(settings.copy(conduitMode = it), false, "conduitMode")
                showConduitModePicker = false
            },
            onDismiss = { showConduitModePicker = false }
        )
    }
    if (showConduitTimeoutPicker) {
        ConduitTimeoutPickerSheet(
            current = settings.conduitTimeoutSeconds,
            onSelect = {
                onPersist(settings.copy(conduitTimeoutSeconds = it), false, "conduitTimeout")
                showConduitTimeoutPicker = false
            },
            onDismiss = { showConduitTimeoutPicker = false }
        )
    }
    if (showVpnOnDemandModePicker) {
        VpnOnDemandModePickerSheet(
            current = settings.vpnOnDemandMode,
            onSelect = {
                onPersist(settings.copy(vpnOnDemandMode = it), false, "vpnOnDemandMode")
                showVpnOnDemandModePicker = false
            },
            onDismiss = { showVpnOnDemandModePicker = false }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.azadi_reset_defaults)) },
            text = { Text(stringResource(R.string.azadi_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onPersist(settingsStore.resetToDefaults(settings), true, "resetDefaults")
                    showResetDialog = false
                }) { Text(stringResource(R.string.lbl_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.lbl_no)) }
            }
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1C1C1E)) {
        options.forEach { protocol ->
            val enabled = protocol != "conduit" || conduitAllowed
            TextButton(
                onClick = { if (enabled) { onSelect(protocol); onDismiss() } },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(protocolDisplayName(protocol), color = if (enabled) Color.White else AppColors.SubtitleText)
            }
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1C1C1E)) {
        options.forEach { (code, label) ->
            TextButton(
                onClick = { onSelect(code); onDismiss() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(label, color = Color.White)
            }
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1C1C1E)) {
        options.forEach { mode ->
            TextButton(onClick = { onSelect(mode) }, modifier = Modifier.fillMaxWidth()) {
                Text(conduitModeDisplayName(mode), color = if (mode == current) AppColors.NavBlue else Color.White)
            }
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1C1C1E)) {
        options.forEach { seconds ->
            TextButton(onClick = { onSelect(seconds) }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    conduitTimeoutLabel(seconds),
                    color = if (seconds == current) AppColors.NavBlue else Color.White
                )
            }
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1C1C1E)) {
        options.forEach { mode ->
            TextButton(onClick = { onSelect(mode) }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    vpnOnDemandModeLabel(mode),
                    color = if (mode == current) AppColors.NavBlue else Color.White
                )
            }
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

fun copyText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, R.string.azadi_copied, Toast.LENGTH_SHORT).show()
}
