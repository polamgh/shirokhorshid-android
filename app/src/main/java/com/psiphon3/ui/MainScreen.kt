package com.psiphon3.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psiphon3.R
import com.psiphon3.azadi.TunnelStatistics
import com.psiphon3.psiphonlibrary.PsiphonConstants
import com.psiphon3.ui.theme.AppColors

@Composable
fun MainScreen(
    connectionStatus: VpnConnectionStatus,
    statusMessage: String,
    selectedRegionCode: String,
    connectedLocationLine: String?,
    regionHintLine: String?,
    durationText: String,
    connectedProtocol: String?,
    pingMs: Long,
    conduitStatusLine: String,
    conduitStatusHistory: List<String>,
    showConduitCard: Boolean,
    errorMessage: String?,
    diagnosticsSummary: String?,
    leakSummary: String?,
    proxyOnlyAddress: String?,
    findBestRunning: Boolean,
    findBestProgress: String?,
    savedBestLabel: String?,
    onToggleClick: () -> Unit,
    onRegionSelected: (String) -> Unit,
    onRefreshPing: () -> Unit,
    onFindBestClick: () -> Unit,
    onConnectBestClick: () -> Unit,
    onRetryClick: () -> Unit,
    onOpenLogs: () -> Unit,
    onSupportClick: () -> Unit,
    trafficStats: TrafficStats
) {
    var showRegionPicker by remember { mutableStateOf(false) }
    var diagnosticsExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DashboardHeader(onSupportClick = onSupportClick)

            Spacer(modifier = Modifier.height(6.dp))
            IranFlagStripe(modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(modifier = Modifier.height(10.dp))

            if (!errorMessage.isNullOrBlank() && connectionStatus != VpnConnectionStatus.CONNECTED) {
                ErrorBanner(message = errorMessage, onRetry = onRetryClick)
                Spacer(modifier = Modifier.height(8.dp))
            }

            StatusHeroCard(
                connectionStatus = connectionStatus,
                statusMessage = statusMessage,
                durationText = durationText,
                connectedProtocol = connectedProtocol,
                pingMs = pingMs,
                onRefreshPing = onRefreshPing
            )

            if (showConduitCard) {
                ConduitProgressCard(
                    currentLine = conduitStatusLine,
                    history = conduitStatusHistory
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            ConnectPowerButton(
                status = connectionStatus,
                isEnabled = connectionStatus != VpnConnectionStatus.WAITING,
                onClick = onToggleClick
            )

            Spacer(modifier = Modifier.height(10.dp))

            LocationCard(
                selectedRegionCode = selectedRegionCode,
                connectedLocationLine = connectedLocationLine,
                regionHintLine = regionHintLine,
                publicIp = trafficStats.ipAddress,
                onRegionClick = { showRegionPicker = true }
            )

            if (!proxyOnlyAddress.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                InfoBanner(
                    text = stringResource(R.string.azadi_proxy_only_card) + "\n" + proxyOnlyAddress,
                    tint = AppColors.GlowGreen
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            TrafficSection(trafficStats = trafficStats)

            if (!diagnosticsSummary.isNullOrBlank() ||
                !leakSummary.isNullOrBlank() ||
                !proxyOnlyAddress.isNullOrBlank() ||
                findBestRunning ||
                !findBestProgress.isNullOrBlank() ||
                !savedBestLabel.isNullOrBlank()
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                DiagnosticsRow(
                    expanded = diagnosticsExpanded,
                    onToggle = { diagnosticsExpanded = !diagnosticsExpanded },
                    summary = diagnosticsSummary,
                    leak = leakSummary,
                    findBestRunning = findBestRunning,
                    findBestProgress = findBestProgress,
                    savedBestLabel = savedBestLabel,
                    onFindBestClick = onFindBestClick,
                    onConnectBestClick = onConnectBestClick,
                    onOpenLogs = onOpenLogs
                )
            }
        }
    }

    val context = LocalContext.current
    val availableRegions = remember(showRegionPicker) {
        if (showRegionPicker) com.psiphon3.azadi.PsiphonRegionList.getAvailable(context) else emptyList()
    }

    if (showRegionPicker) {
        RegionPickerSheet(
            selectedRegionCode = selectedRegionCode,
            onRegionSelected = onRegionSelected,
            onDismiss = { showRegionPicker = false },
            regionCodes = availableRegions
        )
    }
}

@Composable
private fun DashboardHeader(onSupportClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.app_name),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 18.sp
            )
        }

        Surface(
            onClick = onSupportClick,
            shape = CircleShape,
            color = AppColors.IranGreen.copy(alpha = 0.15f),
            modifier = Modifier.height(32.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = AppColors.IranGreenBright,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.dashboard_support),
                    color = AppColors.IranGreenBright,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.dashboard_subtitle),
        color = AppColors.SubtitleText,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StatusHeroCard(
    connectionStatus: VpnConnectionStatus,
    statusMessage: String,
    durationText: String,
    connectedProtocol: String?,
    pingMs: Long,
    onRefreshPing: () -> Unit
) {
    val targetDotColor = when (connectionStatus) {
        VpnConnectionStatus.CONNECTED -> AppColors.GlowGreen
        VpnConnectionStatus.CONNECTING, VpnConnectionStatus.WAITING_FOR_NETWORK -> AppColors.IranWhite
        VpnConnectionStatus.WAITING -> AppColors.SubtitleText
        VpnConnectionStatus.ERROR -> AppColors.IranRed
        VpnConnectionStatus.DISCONNECTED -> Color(0xFF8E8E93)
    }
    val statusDotColor by animateColorAsState(targetDotColor, label = "dotColor")

    val targetTextColor = when (connectionStatus) {
        VpnConnectionStatus.CONNECTED -> AppColors.GlowGreen
        VpnConnectionStatus.CONNECTING, VpnConnectionStatus.WAITING_FOR_NETWORK -> Color.White
        VpnConnectionStatus.WAITING -> AppColors.SubtitleText
        VpnConnectionStatus.ERROR -> AppColors.IranRed
        VpnConnectionStatus.DISCONNECTED -> Color.White
    }
    val statusTextColor by animateColorAsState(targetTextColor, label = "statusTextColor")

    GlassCard(elevated = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C2E))
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(statusDotColor)
                        .border(2.5.dp, statusDotColor.copy(alpha = 0.25f), CircleShape)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dashboard_status),
                    color = AppColors.SubtitleText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = statusMessage,
                    color = statusTextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!connectedProtocol.isNullOrBlank() &&
                    connectionStatus == VpnConnectionStatus.CONNECTED
                ) {
                    Spacer(Modifier.height(1.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("connectedProtocolLabel")
                    ) {
                        Icon(
                            Icons.Default.AccountTree,
                            contentDescription = null,
                            tint = AppColors.GlowGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.dashboard_protocol_line, connectedProtocol),
                            color = AppColors.GlowGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = durationText,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.width(14.dp))
                    Icon(
                        Icons.Default.SignalCellularAlt,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (connectionStatus == VpnConnectionStatus.CONNECTED && pingMs >= 0) {
                            stringResource(R.string.dashboard_ping_ms, pingMs)
                        } else {
                            stringResource(R.string.dashboard_ping_unavailable)
                        },
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                    IconButton(
                        onClick = onRefreshPing,
                        enabled = connectionStatus == VpnConnectionStatus.CONNECTED,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.azadi_refresh_ping),
                            tint = Color.White.copy(alpha = if (connectionStatus == VpnConnectionStatus.CONNECTED) 0.55f else 0.35f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConduitProgressCard(
    currentLine: String,
    history: List<String>
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Hub,
                contentDescription = null,
                tint = AppColors.GlowGreen,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    stringResource(R.string.azadi_conduit_live_title),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    stringResource(R.string.azadi_conduit_live_subtitle),
                    color = AppColors.SubtitleText,
                    fontSize = 11.sp
                )
            }
        }
        if (currentLine.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                currentLine,
                color = AppColors.GlowGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("conduitStatusLine")
            )
        }
        val historyLines = history.take(TunnelStatistics.MAX_HISTORY_UI)
        if (historyLines.isNotEmpty()) {
            HorizontalDivider(
                color = AppColors.CardStroke,
                modifier = Modifier.padding(vertical = 10.dp)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.testTag("conduitStatusHistory")
            ) {
                historyLines.forEach { line ->
                    Text(
                        text = "· $line",
                        color = AppColors.SubtitleText,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TrafficSection(trafficStats: TrafficStats) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.dashboard_traffic),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrafficStatCard(
                title = stringResource(R.string.dashboard_download),
                value = trafficStats.downloadSpeed,
                icon = Icons.Default.ArrowDownward,
                iconBackground = AppColors.GlowGreen,
                modifier = Modifier.weight(1f)
            )
            TrafficStatCard(
                title = stringResource(R.string.dashboard_upload),
                value = trafficStats.uploadSpeed,
                icon = Icons.Default.ArrowUpward,
                iconBackground = AppColors.IranRed,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrafficStatCard(
                title = stringResource(R.string.dashboard_total_download),
                value = trafficStats.totalDownload,
                icon = Icons.Default.Storage,
                iconBackground = AppColors.GlowGreen,
                modifier = Modifier.weight(1f)
            )
            TrafficStatCard(
                title = stringResource(R.string.dashboard_total_upload),
                value = trafficStats.totalUpload,
                icon = Icons.Default.CloudUpload,
                iconBackground = AppColors.IranRed,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.dashboard_traffic_footer),
            color = AppColors.SubtitleText,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun DiagnosticsRow(
    expanded: Boolean,
    onToggle: () -> Unit,
    summary: String?,
    leak: String?,
    findBestRunning: Boolean,
    findBestProgress: String?,
    savedBestLabel: String?,
    onFindBestClick: () -> Unit,
    onConnectBestClick: () -> Unit,
    onOpenLogs: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.dashboard_connection_diagnostics),
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = AppColors.SubtitleText
            )
        }
        AnimatedVisibility(expanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                if (!summary.isNullOrBlank()) {
                    Text(summary, color = AppColors.SubtitleText, fontSize = 13.sp)
                }
                if (!leak.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(leak, color = AppColors.SubtitleText, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.azadi_find_best),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    stringResource(R.string.azadi_find_best_hint),
                    color = AppColors.SubtitleText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (findBestRunning && !findBestProgress.isNullOrBlank()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text(
                        stringResource(R.string.azadi_find_best_running, findBestProgress),
                        color = AppColors.IranWhite,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (!savedBestLabel.isNullOrBlank()) {
                    Text(savedBestLabel, color = AppColors.GlowGreen, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Button(onClick = onFindBestClick, enabled = !findBestRunning) {
                        Text(stringResource(R.string.azadi_find_best))
                    }
                    if (!savedBestLabel.isNullOrBlank()) {
                        OutlinedButton(onClick = onConnectBestClick) {
                            Text(stringResource(R.string.azadi_connect_best))
                        }
                    }
                }
                TextButton(onClick = onOpenLogs, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.List, contentDescription = null, tint = AppColors.SubtitleText)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.azadi_recent_logs), color = AppColors.SubtitleText)
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, contentDescription = null, tint = AppColors.IranRed)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.azadi_dashboard_error),
                    color = AppColors.IranRed,
                    fontWeight = FontWeight.Bold
                )
                Text(message, color = Color.White, fontSize = 13.sp)
            }
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.azadi_error_retry), color = AppColors.GlowGreen)
            }
        }
    }
}

@Composable
private fun InfoBanner(text: String, tint: Color) {
    GlassCard {
        Text(text, color = tint, fontSize = 13.sp)
    }
}

data class TrafficStats(
    val downloadSpeed: String = "0 B/s",
    val uploadSpeed: String = "0 B/s",
    val totalDownload: String = "0 B",
    val totalUpload: String = "0 B",
    val ipAddress: String = "—",
    val connectedCountry: String = ""
)

@Composable
fun resolveRegionHintLine(
    selectedRegionCode: String,
    isConnected: Boolean
): String? {
    if (isConnected) return null
    return if (selectedRegionCode == PsiphonConstants.REGION_CODE_ANY || selectedRegionCode.isEmpty()) {
        regionDisplayName(PsiphonConstants.REGION_CODE_ANY)
    } else {
        stringResource(R.string.dashboard_region_next_connect)
    }
}

@Composable
fun resolveConnectedLocationLine(
    city: String?,
    countryName: String?,
    clientRegionCode: String?
): String? {
    val cityTrim = city?.trim().orEmpty()
    val countryTrim = countryName?.trim().orEmpty()
    if (cityTrim.isNotEmpty() && countryTrim.isNotEmpty()) return "$cityTrim, $countryTrim"
    if (cityTrim.isNotEmpty()) return cityTrim
    if (countryTrim.isNotEmpty()) return countryTrim
    if (!clientRegionCode.isNullOrBlank()) {
        val region = ALL_REGIONS.find { it.code.equals(clientRegionCode, ignoreCase = true) }
        if (region != null) return stringResource(region.nameResId)
        return clientRegionCode
    }
    return null
}
