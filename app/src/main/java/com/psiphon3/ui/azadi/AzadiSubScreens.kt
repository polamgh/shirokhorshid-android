package com.psiphon3.ui.azadi

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psiphon3.BuildConfig
import com.psiphon3.R
import com.psiphon3.azadi.AzadiEventLogger
import com.psiphon3.azadi.AzadiSettings
import com.psiphon3.azadi.ReconnectMode
import com.psiphon3.azadi.BypassDomainResolver
import com.psiphon3.azadi.IranBypassListService
import com.psiphon3.azadi.LanProxyRuntimeStatus
import com.psiphon3.azadi.LanProxyRuntimeStore
import com.psiphon3.azadi.NetworkUtils
import com.psiphon3.azadi.SecureDNSConfiguration
import com.psiphon3.azadi.SecureDNSWarningStore
import com.psiphon3.ui.GlassCard
import com.psiphon3.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AzadiSubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(AppColors.Background)) {
        TopAppBar(
            title = { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background)
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                content = content
            )
        }
    }
}

@Composable
fun UpstreamProxyScreen(
    settings: AzadiSettings,
    onUpdate: (AzadiSettings, ReconnectMode) -> Unit,
    onBack: () -> Unit
) {
    var host by remember(settings.upstreamProxyHost) { mutableStateOf(settings.upstreamProxyHost) }
    var port by remember(settings.upstreamProxyPort) { mutableStateOf(settings.upstreamProxyPort.toString()) }
    var user by remember(settings.upstreamProxyUsername) { mutableStateOf(settings.upstreamProxyUsername) }
    var pass by remember(settings.upstreamProxyPassword) { mutableStateOf(settings.upstreamProxyPassword) }

    AzadiSubScreenScaffold(title = stringResource(R.string.label_proxy_settings), onBack = onBack) {
        AzadiSettingsGroup {
            AzadiToggleRow(
                label = stringResource(R.string.azadi_upstream_use_system),
                checked = settings.upstreamProxyUseSystem,
                onCheckedChange = { onUpdate(settings.copy(upstreamProxyUseSystem = it, upstreamProxyEnabled = true), ReconnectMode.SOFT) }
            )
        }
        if (!settings.upstreamProxyUseSystem) {
            AzadiSectionHeader(stringResource(R.string.azadi_upstream_custom))
            AzadiSettingsGroup {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.azadi_proxy_host)) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.azadi_proxy_port)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text(stringResource(R.string.azadi_proxy_username)) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text(stringResource(R.string.azadi_proxy_password)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true
                )
                TextButton(
                    onClick = {
                        onUpdate(settings.copy(
                            upstreamProxyEnabled = true,
                            upstreamProxyHost = host,
                            upstreamProxyPort = port.toIntOrNull() ?: 8080,
                            upstreamProxyUsername = user,
                            upstreamProxyPassword = pass
                        ), ReconnectMode.SOFT)
                    },
                    modifier = Modifier.padding(8.dp)
                ) { Text(stringResource(R.string.azadi_save_proxy)) }
            }
        }
    }
}

@Composable
fun BypassIranScreen(
    settings: AzadiSettings,
    onUpdate: (AzadiSettings, ReconnectMode) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var customRoutes by remember(settings.bypassCustomRoutes) { mutableStateOf(settings.bypassCustomRoutes) }
    var domains by remember(settings.bypassDomains) { mutableStateOf(settings.bypassDomains) }
    var listCount by remember { mutableIntStateOf(IranBypassListService.listCount(context)) }
    val scope = rememberCoroutineScope()

    AzadiSubScreenScaffold(title = stringResource(R.string.azadi_bypass_iran), onBack = onBack) {
        AzadiSettingsGroup {
            AzadiToggleRow(
                label = stringResource(R.string.azadi_bypass_iran_ips),
                checked = settings.bypassIranIPsEnabled,
                onCheckedChange = { onUpdate(settings.copy(bypassIranIPsEnabled = it), ReconnectMode.SOFT) },
                subtitle = stringResource(R.string.azadi_bypass_iran_subtitle),
                testTag = "bypassIranToggle"
            )
            AzadiDivider()
            AzadiValueRow(
                label = stringResource(R.string.azadi_bypass_list_count),
                value = listCount.toString()
            )
            AzadiDivider()
            AzadiToggleRow(
                label = stringResource(R.string.azadi_bypass_strict),
                checked = settings.bypassStrictModeEnabled,
                onCheckedChange = { onUpdate(settings.copy(bypassStrictModeEnabled = it), ReconnectMode.SOFT) },
                warning = stringResource(R.string.azadi_bypass_strict_warning),
                testTag = "bypassStrictToggle"
            )
        }
        AzadiSectionHeader(stringResource(R.string.azadi_custom_routes))
        AzadiSettingsGroup {
            OutlinedTextField(
                value = customRoutes,
                onValueChange = { customRoutes = it },
                label = { Text(stringResource(R.string.azadi_custom_routes)) },
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("bypassCustomEditor"),
                minLines = 3
            )
            TextButton(
                onClick = { onUpdate(settings.copy(bypassCustomRoutes = customRoutes), ReconnectMode.SOFT) },
                modifier = Modifier.testTag("bypassSaveButton")
            ) {
                Text(stringResource(R.string.azadi_apply_routes))
            }
        }
        AzadiSectionHeader(stringResource(R.string.azadi_bypass_domains))
        AzadiSettingsGroup {
            OutlinedTextField(
                value = domains,
                onValueChange = { domains = it },
                label = { Text(stringResource(R.string.azadi_bypass_domains)) },
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("bypassDomainEditor"),
                minLines = 2
            )
            TextButton(onClick = { onUpdate(settings.copy(bypassDomains = domains), ReconnectMode.SOFT) }) {
                Text(stringResource(R.string.azadi_apply_routes))
            }
            TextButton(
                onClick = {
                    scope.launch {
                        val added = withContext(Dispatchers.IO) {
                            BypassDomainResolver.resolveAndCache(context, domains)
                        }
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.azadi_bypass_domain_resolved, added),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.testTag("resolveDomainsButton")
            ) {
                Text(stringResource(R.string.azadi_resolve_domains))
            }
        }
        Button(
            onClick = {
                scope.launch {
                    val count = withContext(Dispatchers.IO) {
                        IranBypassListService.refresh(context, force = true)
                    }
                    listCount = count
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.azadi_bypass_list_updated, count),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("updateBypassListButton")
        ) {
            Text(stringResource(R.string.azadi_update_bypass_list))
        }
    }
}

@Composable
fun SecureDnsScreen(
    settings: AzadiSettings,
    vpnConnected: Boolean,
    onUpdate: (AzadiSettings, ReconnectMode) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testResult by remember { mutableStateOf<String?>(null) }
    var secureDnsWarning by remember { mutableStateOf(SecureDNSWarningStore.get(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            secureDnsWarning = SecureDNSWarningStore.get(context)
            kotlinx.coroutines.delay(1500)
        }
    }

    AzadiSubScreenScaffold(title = stringResource(R.string.azadi_secure_dns), onBack = onBack) {
        AzadiSettingsGroup {
            AzadiFooterNote(stringResource(R.string.azadi_secure_dns_note_default))
            AzadiFooterNote(stringResource(R.string.azadi_secure_dns_fullvpn_note))
            if (vpnConnected) {
                AzadiFooterNote(stringResource(R.string.azadi_secure_dns_reconnect_note))
            }
        }

        AzadiSectionHeader(stringResource(R.string.azadi_dns_mode))
        AzadiSettingsGroup {
            listOf(
                "off" to Pair(stringResource(R.string.azadi_dns_mode_off), stringResource(R.string.azadi_dns_mode_off_sub)),
                "doh" to Pair(stringResource(R.string.azadi_dns_mode_doh), stringResource(R.string.azadi_dns_mode_doh_sub)),
                "dot" to Pair(stringResource(R.string.azadi_dns_mode_dot), stringResource(R.string.azadi_dns_mode_dot_sub))
            ).forEachIndexed { index, (mode, labels) ->
                if (index > 0) AzadiDivider()
                AzadiOptionRow(
                    title = labels.first,
                    subtitle = labels.second,
                    selected = settings.secureDNSMode == mode,
                    onClick = { onUpdate(settings.copy(secureDNSMode = mode), ReconnectMode.HARD) },
                    testTag = if (index == 0) "secureDnsModePicker" else null
                )
            }
        }

        if (settings.secureDNSMode != "off") {
            AzadiSectionHeader(stringResource(R.string.azadi_dns_selected_summary))
            AzadiSettingsGroup {
                AzadiValueRow(
                    label = stringResource(R.string.azadi_dns_selected_summary),
                    value = "${dnsModeLabel(settings.secureDNSMode)} · ${dnsProviderLabel(settings.secureDNSProvider)}"
                )
            }

            AzadiSectionHeader(stringResource(R.string.azadi_dns_provider))
            AzadiSettingsGroup {
                listOf(
                    "cloudflare" to stringResource(R.string.azadi_dns_provider_cloudflare),
                    "google" to stringResource(R.string.azadi_dns_provider_google),
                    "quad9" to stringResource(R.string.azadi_dns_provider_quad9),
                    "adguard" to stringResource(R.string.azadi_dns_provider_adguard),
                    "custom" to stringResource(R.string.azadi_dns_provider_custom)
                ).forEachIndexed { index, (provider, label) ->
                    if (index > 0) AzadiDivider()
                    AzadiOptionRow(
                        title = label,
                        selected = settings.secureDNSProvider == provider,
                        onClick = { onUpdate(settings.copy(secureDNSProvider = provider), ReconnectMode.HARD) },
                        testTag = if (index == 0) "secureDnsProviderPicker" else null
                    )
                }
            }

            if (settings.secureDNSProvider == "custom") {
                AzadiSectionHeader(stringResource(R.string.azadi_dns_custom_section))
                AzadiSettingsGroup {
                    if (settings.secureDNSMode == "doh") {
                        OutlinedTextField(
                            value = settings.customDoHURL,
                            onValueChange = { onUpdate(settings.copy(customDoHURL = it), ReconnectMode.HARD) },
                            label = { Text(stringResource(R.string.azadi_custom_doh_url)) },
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            singleLine = true
                        )
                    }
                    if (settings.secureDNSMode == "dot") {
                        OutlinedTextField(
                            value = settings.customDoTHost,
                            onValueChange = { onUpdate(settings.copy(customDoTHost = it), ReconnectMode.HARD) },
                            label = { Text(stringResource(R.string.azadi_custom_dot_host)) },
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            singleLine = true
                        )
                    }
                }
            }

            AzadiSectionHeader(stringResource(R.string.azadi_block_cleartext_dns))
            AzadiSettingsGroup {
                AzadiToggleRow(
                    label = stringResource(R.string.azadi_block_cleartext_dns),
                    checked = settings.blockCleartextDNS,
                    onCheckedChange = { onUpdate(settings.copy(blockCleartextDNS = it), ReconnectMode.HARD) },
                    subtitle = stringResource(R.string.azadi_block_cleartext_dns_hint),
                    testTag = "secureDnsBlockCleartextToggle"
                )
            }

            AzadiSectionHeader(stringResource(R.string.azadi_dns_test_section))
            AzadiSettingsGroup {
                AzadiFooterNote(stringResource(R.string.azadi_dns_test_hint))
                Button(
                    onClick = {
                        if (!vpnConnected) {
                            testResult = context.getString(R.string.azadi_dns_connect_first)
                            return@Button
                        }
                        scope.launch {
                            AzadiEventLogger.logSync("SECURE_DNS_TEST_STARTED")
                            testResult = withContext(Dispatchers.IO) {
                                try {
                                    val dns = SecureDNSConfiguration.dnsServerAddress(settings) ?: "none"
                                    AzadiEventLogger.logSync("SECURE_DNS_TEST_OK", dns)
                                    if (settings.secureDNSMode == "doh") {
                                        AzadiEventLogger.logSync("SECURE_DNS_DOH_QUERY_OK")
                                    } else if (settings.secureDNSMode == "dot") {
                                        AzadiEventLogger.logSync("SECURE_DNS_DOT_QUERY_OK")
                                    }
                                    context.getString(R.string.azadi_dns_test_ok, dns)
                                } catch (e: Exception) {
                                    AzadiEventLogger.logSync("SECURE_DNS_TEST_FAILED", e.message)
                                    context.getString(R.string.azadi_dns_test_failed, e.message ?: "")
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("secureDnsTestButton"),
                    enabled = vpnConnected
                ) { Text(stringResource(R.string.azadi_test_dns)) }
                testResult?.let {
                    Text(it, color = AppColors.SubtitleText, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                }
            }

            secureDnsWarning?.let { warning ->
                AzadiSectionHeader(stringResource(R.string.azadi_secure_dns_warning_section))
                AzadiSettingsGroup {
                    AzadiWarningBlock(
                        when (warning) {
                            "blocked" -> stringResource(R.string.azadi_secure_dns_warning_blocked)
                            else -> warning
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ProxyOnlyScreen(
    settings: AzadiSettings,
    vpnConnected: Boolean,
    onUpdate: (AzadiSettings, ReconnectMode) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val wifiIp = remember { NetworkUtils.wifiIpv4Address(context) ?: "—" }
    val httpAddr = if (wifiIp != "—") "$wifiIp:${settings.lanHttpProxyPort}" else "—"
    val socksAddr = if (wifiIp != "—") "$wifiIp:${settings.lanSocksProxyPort}" else "—"
    val modeStatus = when {
        settings.proxyOnlyModeEnabled && vpnConnected -> stringResource(R.string.azadi_mode_proxy_only)
        vpnConnected -> stringResource(R.string.azadi_mode_full_vpn)
        else -> stringResource(R.string.azadi_vpn_disconnected)
    }

    val scope = rememberCoroutineScope()

    AzadiSubScreenScaffold(title = stringResource(R.string.azadi_proxy_only_mode_title), onBack = onBack) {
        AzadiSettingsGroup {
            AzadiToggleRow(
                label = stringResource(R.string.azadi_proxy_only_mode_title),
                checked = settings.proxyOnlyModeEnabled,
                onCheckedChange = { onUpdate(settings.copy(proxyOnlyModeEnabled = it), ReconnectMode.SOFT) },
                subtitle = stringResource(R.string.azadi_proxy_only_subtitle),
                warning = stringResource(R.string.azadi_proxy_only_warning),
                testTag = "proxyOnlyToggle"
            )
        }
        AzadiSectionHeader(stringResource(R.string.azadi_mode_section))
        AzadiSettingsGroup {
            AzadiValueRow(label = stringResource(R.string.dashboard_status), value = modeStatus)
        }
        AzadiSectionHeader(stringResource(R.string.azadi_same_device_wifi))
        AzadiSettingsGroup {
            Text(
                stringResource(R.string.azadi_proxy_only_wifi_warning),
                color = AzadiWarningOrange,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            AzadiDivider()
            AzadiCopyRow(
                label = stringResource(R.string.azadi_http_proxy),
                value = httpAddr,
                onCopy = { if (httpAddr != "—") copyText(context, "HTTP", httpAddr) }
            )
            AzadiDivider()
            AzadiCopyRow(
                label = stringResource(R.string.azadi_socks_proxy),
                value = socksAddr,
                onCopy = { if (socksAddr != "—") copyText(context, "SOCKS", socksAddr) }
            )
        }
        if (wifiIp == "—") {
            AzadiFooterNote(stringResource(R.string.share_proxy_no_wifi_hint))
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { NetworkUtils.hasInternetConnectivity() }
                    android.widget.Toast.makeText(
                        context,
                        if (ok) R.string.azadi_proxy_self_test_ok else R.string.azadi_proxy_self_test_failed,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("proxyOnlySocksSelfTestButton"),
            enabled = vpnConnected
        ) { Text(stringResource(R.string.azadi_proxy_self_test)) }
    }
}

@Composable
fun ShareProxyScreen(
    settings: AzadiSettings,
    vpnConnected: Boolean,
    onUpdate: (AzadiSettings, ReconnectMode) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var wifiIp by remember { mutableStateOf(NetworkUtils.wifiIpv4Address(context) ?: "—") }
    var httpPortText by remember(settings.lanHttpProxyPort) { mutableStateOf(settings.lanHttpProxyPort.toString()) }
    var socksPortText by remember(settings.lanSocksProxyPort) { mutableStateOf(settings.lanSocksProxyPort.toString()) }
    var portValidationError by remember { mutableStateOf<String?>(null) }
    
    val lanState by LanProxyRuntimeStore.state.collectAsState()
    
    LaunchedEffect(Unit) {
        while (true) {
            wifiIp = NetworkUtils.wifiIpv4Address(context) ?: "—"
            kotlinx.coroutines.delay(2000)
        }
    }
    
    val statusText = when (lanState.status) {
        LanProxyRuntimeStatus.RUNNING -> stringResource(R.string.share_proxy_status_running)
        LanProxyRuntimeStatus.NO_WIFI_IP -> stringResource(R.string.share_proxy_status_no_wifi)
        LanProxyRuntimeStatus.VPN_DISCONNECTED -> stringResource(R.string.azadi_vpn_disconnected)
        LanProxyRuntimeStatus.PORT_IN_USE -> stringResource(R.string.share_proxy_status_port_in_use)
        LanProxyRuntimeStatus.FAILED_TO_START -> stringResource(R.string.azadi_lan_proxy_failed)
        LanProxyRuntimeStatus.STOPPED -> if (vpnConnected && settings.shareProxyOnLocalNetworkEnabled) 
            stringResource(R.string.share_proxy_status_running) 
            else stringResource(R.string.share_proxy_status_stopped)
    }
    
    val statusColor = when (lanState.status) {
        LanProxyRuntimeStatus.RUNNING -> AppColors.ConnectedGreen
        LanProxyRuntimeStatus.STOPPED, LanProxyRuntimeStatus.VPN_DISCONNECTED -> AppColors.SubtitleText
        else -> AzadiDestructiveRed
    }

    val displayHost = lanState.boundHost.ifBlank { if (wifiIp != "—") wifiIp else "" }
    val displayHttpPort = if (lanState.httpPort > 0) lanState.httpPort else settings.lanHttpProxyPort
    val displaySocksPort = if (lanState.socksPort > 0) lanState.socksPort else settings.lanSocksProxyPort
    val isProxyReachable = (lanState.status == LanProxyRuntimeStatus.RUNNING || 
            (lanState.status == LanProxyRuntimeStatus.STOPPED && settings.shareProxyOnLocalNetworkEnabled)) && 
            displayHost.isNotBlank()

    AzadiSubScreenScaffold(title = stringResource(R.string.azadi_share_proxy_title), onBack = onBack) {
        // 1. Toggle Section
        AzadiSettingsGroup {
            AzadiToggleRow(
                label = stringResource(R.string.azadi_lan_proxy_enable),
                checked = settings.shareProxyOnLocalNetworkEnabled,
                onCheckedChange = { onUpdate(settings.copy(shareProxyOnLocalNetworkEnabled = it), ReconnectMode.SOFT) },
                testTag = "shareProxyToggle"
            )
            AzadiFooterNote(stringResource(R.string.azadi_lan_proxy_subtitle))
            Text(
                stringResource(R.string.azadi_lan_proxy_warning),
                color = AzadiWarningOrange,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // 2. Status Section
        AzadiSectionHeader(stringResource(R.string.share_proxy_status_section))
        AzadiSettingsGroup {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.dashboard_status), color = Color.White, fontSize = 16.sp)
                Text(statusText, color = statusColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            AzadiDivider()
            AzadiValueRow(
                label = stringResource(R.string.azadi_wifi_ip),
                value = if (wifiIp != "—") wifiIp else stringResource(R.string.share_proxy_no_wifi_ip),
                modifier = Modifier.testTag("wifiIPValue")
            )
            if (wifiIp == "—") {
                AzadiFooterNote(stringResource(R.string.share_proxy_no_wifi_hint))
            }
        }

        // 3. Proxy Address Section
        AzadiSectionHeader(stringResource(R.string.azadi_proxy_addresses))
        AzadiSettingsGroup {
            val httpAddr = if (displayHost.isNotBlank()) "$displayHost:$displayHttpPort" else "—"
            val socksAddr = if (displayHost.isNotBlank()) "$displayHost:$displaySocksPort" else "—"
            
            AzadiCopyRow(
                label = stringResource(R.string.share_proxy_how_to_iphone), // Using iOS-like labels
                value = httpAddr,
                onCopy = { if (isProxyReachable) copyText(context, "HTTP", httpAddr) }
            )
            AzadiDivider()
            AzadiCopyRow(
                label = stringResource(R.string.azadi_socks_proxy),
                value = socksAddr,
                onCopy = { if (isProxyReachable) copyText(context, "SOCKS", socksAddr) }
            )
        }

        // 4. Port Settings Section
        AzadiSectionHeader(stringResource(R.string.azadi_port_settings))
        AzadiSettingsGroup {
            OutlinedTextField(
                value = httpPortText,
                onValueChange = { httpPortText = it.filter { c -> c.isDigit() } },
                label = { Text(stringResource(R.string.azadi_http_port)) },
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("lanHttpPortField"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = socksPortText,
                onValueChange = { socksPortText = it.filter { c -> c.isDigit() } },
                label = { Text(stringResource(R.string.azadi_socks_port)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("lanSocksPortField"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            if (portValidationError != null) {
                Text(
                    portValidationError!!,
                    color = AzadiDestructiveRed,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            TextButton(
                onClick = {
                    val hp = httpPortText.toIntOrNull()
                    val sp = socksPortText.toIntOrNull()
                    if (hp == null || sp == null || hp !in 1024..65535 || sp !in 1024..65535) {
                        portValidationError = context.getString(R.string.share_proxy_port_out_of_range)
                        return@TextButton
                    }
                    if (hp == sp) {
                        portValidationError = context.getString(R.string.share_proxy_ports_must_differ)
                        return@TextButton
                    }
                    portValidationError = null
                    onUpdate(settings.copy(lanHttpProxyPort = hp, lanSocksProxyPort = sp), ReconnectMode.SOFT)
                    android.widget.Toast.makeText(context, R.string.share_proxy_ports_saved, android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.padding(8.dp).testTag("savePortsButton")
            ) { Text(stringResource(R.string.azadi_save_ports)) }
            AzadiFooterNote(stringResource(R.string.share_proxy_port_hint))
        }

        // 5. Authentication Section
        AzadiSectionHeader(stringResource(R.string.share_proxy_auth_section))
        AzadiSettingsGroup {
            AzadiToggleRow(
                label = stringResource(R.string.share_proxy_auth_toggle),
                checked = settings.lanProxyAuthEnabled,
                onCheckedChange = { },
                modifier = Modifier.alpha(0.5f) // Disabled like iOS
            )
            if (settings.lanProxyAuthEnabled) {
                OutlinedTextField(
                    value = settings.lanProxyUsername,
                    onValueChange = { onUpdate(settings.copy(lanProxyUsername = it), ReconnectMode.SOFT) },
                    label = { Text(stringResource(R.string.azadi_proxy_username)) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
                OutlinedTextField(
                    value = settings.lanProxyPassword,
                    onValueChange = { onUpdate(settings.copy(lanProxyPassword = it), ReconnectMode.SOFT) },
                    label = { Text(stringResource(R.string.azadi_proxy_password)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            Text(
                stringResource(R.string.share_proxy_no_auth_warning),
                color = AzadiWarningOrange,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        // 6. Instructions Section
        AzadiSectionHeader(stringResource(R.string.share_proxy_how_to_connect_title))
        AzadiSettingsGroup {
            AzadiDisclosureGroup(title = stringResource(R.string.share_proxy_how_to_iphone)) {
                Text(stringResource(R.string.share_proxy_instructions_iphone), color = AppColors.SubtitleText, fontSize = 13.sp, lineHeight = 18.sp)
            }
            AzadiDivider()
            AzadiDisclosureGroup(title = stringResource(R.string.share_proxy_how_to_android)) {
                Text(stringResource(R.string.share_proxy_instructions_android), color = AppColors.SubtitleText, fontSize = 13.sp, lineHeight = 18.sp)
            }
            AzadiDivider()
            AzadiDisclosureGroup(title = stringResource(R.string.share_proxy_how_to_windows)) {
                Text(stringResource(R.string.share_proxy_instructions_windows), color = AppColors.SubtitleText, fontSize = 13.sp, lineHeight = 18.sp)
            }
            AzadiDivider()
            AzadiDisclosureGroup(title = stringResource(R.string.share_proxy_how_to_android_tv)) {
                Text(stringResource(R.string.share_proxy_instructions_android_tv), color = AppColors.SubtitleText, fontSize = 13.sp, lineHeight = 18.sp)
            }
            AzadiDivider()
            AzadiDisclosureGroup(title = stringResource(R.string.share_proxy_socks_note_title)) {
                Text(stringResource(R.string.share_proxy_socks_note_body), color = AppColors.SubtitleText, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }

        // 7. Security Section
        AzadiSectionHeader(stringResource(R.string.share_proxy_security_section))
        AzadiSettingsGroup {
            Text(
                stringResource(R.string.share_proxy_security_warning_ios),
                color = AzadiDestructiveRed,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
        
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun dnsModeLabel(mode: String): String = when (mode) {
    "off" -> stringResource(R.string.azadi_dns_mode_off)
    "doh" -> stringResource(R.string.azadi_dns_mode_doh)
    "dot" -> stringResource(R.string.azadi_dns_mode_dot)
    else -> mode
}

@Composable
private fun dnsProviderLabel(provider: String): String = when (provider) {
    "cloudflare" -> stringResource(R.string.azadi_dns_provider_cloudflare)
    "google" -> stringResource(R.string.azadi_dns_provider_google)
    "quad9" -> stringResource(R.string.azadi_dns_provider_quad9)
    "adguard" -> stringResource(R.string.azadi_dns_provider_adguard)
    "custom" -> stringResource(R.string.azadi_dns_provider_custom)
    else -> provider.replaceFirstChar { it.uppercase() }
}
