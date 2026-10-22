package com.psiphon3.ui

import com.psiphon3.ui.redesign.FitText

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psiphon3.R
import com.psiphon3.azadi.TunnelStatistics
import com.psiphon3.psiphonlibrary.PsiphonConstants
import com.psiphon3.ui.redesign.AzadiPowerButton
import com.psiphon3.ui.redesign.AzadiWordmark
import com.psiphon3.ui.redesign.MapCoordinate
import com.psiphon3.ui.redesign.RegionPickerSheetV2
import com.psiphon3.ui.redesign.VpnWorldMap
import com.psiphon3.ui.redesign.WorldMapLocations
import com.psiphon3.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

/** Where traffic starts and exits, for the dashboard map and route caption. */
data class DashboardMapState(
    val origin: MapCoordinate? = null,
    val originCountryCode: String? = null,
    val destination: MapCoordinate? = null,
    val destinationCountryCode: String? = null
)

private val HeaderHeight = 100.dp
private val GrabberHeight = 22.dp
private val SheetCorner = 30.dp

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
    trafficStats: TrafficStats,
    mapState: DashboardMapState = DashboardMapState(),
    protocolSelection: String = "auto",
    conduitAllowed: Boolean = true,
    onProtocolSelected: (String) -> Unit = {},
    pingRefreshing: Boolean = false,
    bottomInset: Dp = 62.dp
) {
    var showRegionPicker by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var collapsedSheetHeightPx by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val context = LocalContext.current

    val setExpanded: (Boolean) -> Unit = { value ->
        if (value != expanded) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            expanded = value
        }
    }

    androidx.activity.compose.BackHandler(enabled = expanded) { setExpanded(false) }

    val isConnected = connectionStatus == VpnConnectionStatus.CONNECTED
    val isConnecting = connectionStatus == VpnConnectionStatus.CONNECTING ||
        connectionStatus == VpnConnectionStatus.WAITING_FOR_NETWORK
    val statusTint by animateColorAsState(statusColor(connectionStatus), tween(600), label = "statusTint")

    // Slow-connection hint, like iOS: after 20 s (35 s for CDN fronting) of connecting.
    var showSlowHelp by remember { mutableStateOf(false) }
    LaunchedEffect(isConnecting, protocolSelection) {
        showSlowHelp = false
        if (isConnecting) {
            delay(if (protocolSelection == "cdn_fronting") 35_000L else 20_000L)
            showSlowHelp = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppColors.SurfaceTop, AppColors.SurfaceBottom)))
    ) {
        val maxH = maxHeight
        val compact = maxH < 640.dp
        val collapsedSheetHeight = with(density) { collapsedSheetHeightPx.toDp() }.takeIf { it > 0.dp } ?: 380.dp

        // Status glow behind the map.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.32f)
            drawCircle(
                Brush.radialGradient(
                    listOf(statusTint.copy(alpha = 0.22f), Color.Transparent),
                    center = center,
                    radius = 360.dp.toPx()
                ),
                radius = 360.dp.toPx(),
                center = center
            )
        }

        VpnWorldMap(
            origin = mapState.origin,
            destination = mapState.destination,
            originCountryCode = mapState.originCountryCode,
            destinationCountryCode = mapState.destinationCountryCode,
            isConnected = isConnected,
            isConnecting = isConnecting,
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = HeaderHeight)
                .fillMaxWidth()
                .height((maxH - collapsedSheetHeight - bottomInset - HeaderHeight - 12.dp).coerceAtLeast(120.dp))
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp)
        ) {
            DashboardHeader(onSupportClick = onSupportClick)
            RouteCaption(
                isProtected = isConnected,
                mapState = mapState,
                subtitle = if (isConnected) connectedLocationLine else null,
                publicIp = trafficStats.ipAddress
            )
        }

        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { setExpanded(false) }
            )
        }

        val dragModifier = Modifier.pointerInput(Unit) {
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    if (abs(total) > 36.dp.toPx()) setExpanded(total < 0)
                },
                onVerticalDrag = { _, dy -> total += dy }
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp)
                .padding(bottom = bottomInset)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(max = maxH - 24.dp)
                .onSizeChanged { if (!expanded) collapsedSheetHeightPx = it.height }
                .then(if (!expanded) dragModifier else Modifier)
                .glassSurface(SheetCorner)
                .animateContentSize(spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow))
        ) {
            FlagBand()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(GrabberHeight)
                    .then(dragModifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { setExpanded(!expanded) }
            ) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(AppColors.SecondaryText.copy(alpha = 0.35f))
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState(), enabled = expanded)
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!errorMessage.isNullOrBlank() && !isConnected) {
                        DashboardNotice(
                            icon = Icons.Default.Error,
                            tint = AppColors.IranRed,
                            title = stringResource(R.string.azadi_dashboard_error),
                            message = errorMessage,
                            actionLabel = stringResource(R.string.azadi_error_retry),
                            onAction = onRetryClick
                        )
                    }
                    if (showSlowHelp && isConnecting) {
                        DashboardNotice(
                            icon = Icons.Default.HourglassTop,
                            tint = AppColors.Warning,
                            title = stringResource(R.string.dash2_slow_title),
                            message = stringResource(R.string.dash2_slow_message)
                        )
                    }
                    StatusRow(
                        status = connectionStatus,
                        tint = statusTint,
                        statusMessage = statusMessage,
                        durationText = durationText,
                        protocolLabel = connectedProtocol?.takeIf { isConnected && it.isNotBlank() },
                        expanded = expanded,
                        onToggleDetails = { setExpanded(!expanded) }
                    )
                    AzadiPowerButton(
                        status = connectionStatus,
                        isEnabled = connectionStatus != VpnConnectionStatus.WAITING,
                        onClick = onToggleClick,
                        size = if (compact) 104.dp else 124.dp,
                        contentDescription = stringResource(
                            if (isConnected || isConnecting) R.string.dashboard_action_disconnect else R.string.dashboard_action_connect
                        ),
                        stateDescription = statusMessage,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 2.dp)
                            .testTag("connectButton")
                    )
                    ControlChips(
                        protocolSelection = protocolSelection,
                        conduitAllowed = conduitAllowed,
                        onProtocolSelected = onProtocolSelected,
                        selectedRegionCode = selectedRegionCode,
                        onRegionClick = { showRegionPicker = true },
                        pingText = when {
                            !isConnected -> "—"
                            pingMs >= 0 -> stringResource(R.string.dashboard_ping_ms, pingMs)
                            pingRefreshing -> "…"
                            else -> "—"
                        },
                        pingEnabled = isConnected && !pingRefreshing,
                        pingRefreshing = pingRefreshing,
                        onRefreshPing = onRefreshPing
                    )
                    TrafficRow(trafficStats)
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (showConduitCard) {
                            DashboardSection(stringResource(R.string.azadi_conduit_live_title), Icons.Default.Hub) {
                                Text(stringResource(R.string.azadi_conduit_live_subtitle), color = AppColors.SecondaryText, fontSize = 11.sp)
                                if (conduitStatusLine.isNotBlank()) {
                                    Text(
                                        conduitStatusLine,
                                        color = AppColors.IranGreen,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.testTag("conduitStatusLine")
                                    )
                                }
                                conduitStatusHistory.take(TunnelStatistics.MAX_HISTORY_UI).forEach { line ->
                                    Text("· $line", color = AppColors.SecondaryText, fontSize = 12.sp, lineHeight = 16.sp)
                                }
                            }
                        }
                        if (isConnected && !proxyOnlyAddress.isNullOrBlank()) {
                            DashboardSection(stringResource(R.string.azadi_proxy_only_card), Icons.Default.SwapHorizontalCircle, AppColors.Warning) {
                                CopyRow(stringResource(R.string.dashboard_public_ip), proxyOnlyAddress, context)
                            }
                        }
                        DashboardSection(stringResource(R.string.dashboard_public_ip), Icons.Default.Lan) {
                            CopyRow(stringResource(R.string.dashboard_public_ip), trafficStats.ipAddress, context)
                            if (isConnected && !connectedLocationLine.isNullOrBlank()) {
                                DetailRow(stringResource(R.string.dashboard_region), connectedLocationLine)
                            }
                            if (isConnected && !connectedProtocol.isNullOrBlank()) {
                                DetailRow(stringResource(R.string.azadi_protocol), connectedProtocol)
                            }
                        }
                        DashboardSection(stringResource(R.string.dashboard_connection_diagnostics), Icons.Default.MedicalServices) {
                            if (!diagnosticsSummary.isNullOrBlank()) {
                                Text(diagnosticsSummary, color = AppColors.SecondaryText, fontSize = 12.sp)
                            }
                            if (!leakSummary.isNullOrBlank()) {
                                Text(leakSummary, color = AppColors.SecondaryText, fontSize = 12.sp)
                            }
                            Text(
                                stringResource(R.string.azadi_find_best),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(stringResource(R.string.azadi_find_best_hint), color = AppColors.SecondaryText, fontSize = 12.sp)
                            if (findBestRunning && !findBestProgress.isNullOrBlank()) {
                                LinearProgressIndicator(
                                    color = AppColors.IranGreenBright,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    stringResource(R.string.azadi_find_best_running, findBestProgress),
                                    color = AppColors.IranWhite,
                                    fontSize = 12.sp
                                )
                            }
                            if (!savedBestLabel.isNullOrBlank()) {
                                Text(savedBestLabel, color = AppColors.IranGreenBright, fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = onFindBestClick, enabled = !findBestRunning) {
                                    Text(stringResource(R.string.azadi_find_best), fontSize = 13.sp)
                                }
                                if (!savedBestLabel.isNullOrBlank()) {
                                    OutlinedButton(onClick = onConnectBestClick) {
                                        Text(stringResource(R.string.azadi_connect_best), fontSize = 13.sp)
                                    }
                                }
                            }
                            TextButton(onClick = onOpenLogs, contentPadding = PaddingValues(0.dp)) {
                                Icon(Icons.Default.List, contentDescription = null, tint = AppColors.SecondaryText, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.azadi_recent_logs), color = AppColors.SecondaryText, fontSize = 13.sp)
                            }
                        }
                        Text(
                            stringResource(R.string.dashboard_traffic_footer),
                            color = AppColors.SecondaryText,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (showRegionPicker) {
        val availableRegions = remember { com.psiphon3.azadi.PsiphonRegionList.getAvailable(context) }
        RegionPickerSheetV2(
            selectedRegionCode = selectedRegionCode,
            onRegionSelected = onRegionSelected,
            onDismiss = { showRegionPicker = false },
            regionCodes = availableRegions
        )
    }
}

private fun statusColor(status: VpnConnectionStatus): Color = when (status) {
    VpnConnectionStatus.CONNECTED -> AppColors.IranGreenBright
    VpnConnectionStatus.CONNECTING, VpnConnectionStatus.WAITING_FOR_NETWORK -> AppColors.IranWhite.copy(alpha = 0.9f)
    VpnConnectionStatus.ERROR -> AppColors.IranRed
    VpnConnectionStatus.WAITING, VpnConnectionStatus.DISCONNECTED -> Color.White.copy(alpha = 0.5f)
}

/** Translucent glass panel (Android has no live blur under Compose, so it is a tinted frost). */
fun Modifier.glassSurface(corner: Dp): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .shadow(24.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.45f))
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(Color(0xFF1E2636).copy(alpha = 0.95f), Color(0xFF121826).copy(alpha = 0.97f))
            )
        )
        .border(1.dp, AppColors.GlassStroke, shape)
}

/** Edge-to-edge Iran flag ribbon across the top of the sheet; the rounded clip bends it. */
@Composable
private fun FlagBand() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth().height(4.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().background(AppColors.IranGreen))
            Box(Modifier.weight(1f).fillMaxHeight().background(AppColors.IranWhite))
            Box(Modifier.weight(1f).fillMaxHeight().background(AppColors.IranRed))
        }
    }
}

@Composable
private fun DashboardHeader(onSupportClick: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(10.dp))
            AzadiWordmark(fontSize = 32.sp, color = Color.White, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, AppColors.IranGreen.copy(alpha = 0.25f), CircleShape)
                    .clickable(onClick = onSupportClick)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = AppColors.IranGreen, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    stringResource(R.string.dashboard_support),
                    color = AppColors.IranGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** Where traffic exits: origin → egress while connected, otherwise the exposed origin IP. */
@Composable
private fun RouteCaption(isProtected: Boolean, mapState: DashboardMapState, subtitle: String?, publicIp: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xFF151B27).copy(alpha = 0.78f))
                .border(1.dp, AppColors.GlassStroke, CircleShape)
                .padding(horizontal = 14.dp, vertical = 9.dp)
                .animateContentSize()
        ) {
            Icon(
                if (isProtected) Icons.Default.Shield else Icons.Default.GppBad,
                contentDescription = null,
                tint = if (isProtected) AppColors.IranGreenBright else AppColors.SecondaryText,
                modifier = Modifier.size(15.dp)
            )
            val originFlag = WorldMapLocations.flagEmoji(mapState.originCountryCode)
            if (isProtected) {
                // Connected: the map labels already show both flags, so the caption stays text-only.
                FitText(
                    subtitle ?: stringResource(R.string.dash2_protected),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
            } else {
                originFlag?.let { Text(it, fontSize = 13.sp) }
                FitText(
                    stringResource(R.string.dash2_unprotected),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Box(
                Modifier
                    .size(width = 1.dp, height = 14.dp)
                    .background(AppColors.SecondaryText.copy(alpha = 0.35f))
            )
            FitText(
                publicIp,
                color = AppColors.SecondaryText,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .testTag("publicIPLabel")
            )
        }
    }
}

@Composable
private fun StatusRow(
    status: VpnConnectionStatus,
    tint: Color,
    statusMessage: String,
    durationText: String,
    protocolLabel: String?,
    expanded: Boolean,
    onToggleDetails: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusOrb(status, tint)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            AnimatedContent(
                targetState = statusMessage,
                transitionSpec = {
                    (slideInVertically(spring(dampingRatio = 0.78f, stiffness = 300f)) { it } + fadeIn() +
                        scaleIn(initialScale = 0.92f)) togetherWith
                        (slideOutVertically { -it } + fadeOut())
                },
                label = "status"
            ) { message ->
                FitText(
                    message,
                    color = tint,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    modifier = Modifier.testTag("statusLabel")
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = AppColors.SecondaryText, modifier = Modifier.size(12.dp))
                RollingText(
                    durationText,
                    TextStyle(color = AppColors.SecondaryText, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium),
                    modifier = Modifier.testTag("durationLabel")
                )
                AnimatedVisibility(
                    visible = protocolLabel != null,
                    enter = fadeIn() + expandHorizontallyFromStart(),
                    exit = fadeOut()
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("·", color = AppColors.SecondaryText, fontSize = 12.sp)
                        RollingText(
                            protocolLabel.orEmpty(),
                            TextStyle(color = AppColors.IranGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.testTag("connectedProtocolLabel")
                        )
                    }
                }
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
                .clickable(onClick = onToggleDetails)
        ) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = stringResource(if (expanded) R.string.dash2_hide_details else R.string.dash2_details),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun expandHorizontallyFromStart() =
    androidx.compose.animation.expandHorizontally(expandFrom = Alignment.Start)

/**
 * Animated status badge: radar ripples around a lock when connected, a spinning arc while
 * connecting, a slow breath when idle and a pulse on error.
 */
@Composable
private fun StatusOrb(status: VpnConnectionStatus, tint: Color) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1_000_000_000f }
    }
    val busy = status == VpnConnectionStatus.CONNECTING || status == VpnConnectionStatus.WAITING_FOR_NETWORK ||
        status == VpnConnectionStatus.WAITING
    val icon = when {
        status == VpnConnectionStatus.CONNECTED -> Icons.Default.Lock
        busy -> Icons.Default.Sync
        status == VpnConnectionStatus.ERROR -> Icons.Default.PriorityHigh
        else -> Icons.Default.LockOpen
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
        Canvas(Modifier.size(56.dp)) {
            val t = time
            val c = center
            val breath = sin(t * (if (status == VpnConnectionStatus.ERROR) 5f else 1.6f)) * 0.5f + 0.5f
            if (status == VpnConnectionStatus.CONNECTED) {
                for (ring in 0 until 2) {
                    val phase = (t / 2.2f + ring / 2f) % 1f
                    drawCircle(tint.copy(alpha = 0.55f * (1 - phase)), (12 + 15 * phase).dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
                }
            }
            val glow = 0.14f + (if (status == VpnConnectionStatus.CONNECTED) 0.06f else 0.08f) * breath
            drawCircle(tint.copy(alpha = glow), 21.dp.toPx(), c)
            if (busy) {
                val r = 18.dp.toPx()
                drawArc(
                    Brush.sweepGradient(listOf(tint.copy(alpha = 0f), tint), c),
                    startAngle = (t * 5.5f * 57.2958f) % 360f,
                    sweepAngle = 108f,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                    style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            val scale = when {
                busy -> 0.86f + 0.1f * breath
                status == VpnConnectionStatus.ERROR -> 0.94f + 0.08f * breath
                else -> 1f
            }
            drawCircle(tint, 12.dp.toPx() * scale, c)
        }
        AnimatedContent(
            targetState = icon,
            transitionSpec = { (scaleIn(initialScale = 0.3f) + fadeIn()) togetherWith fadeOut() },
            label = "orbIcon"
        ) { vector ->
            Icon(
                vector,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(13.dp)
                    .rotate(if (busy) (time * 3f * 57.2958f) % 360f else 0f)
            )
        }
    }
}

/**
 * Rolls changed characters (timers, speeds, protocol names) like iOS numericText.
 *
 * Latin/numeric values are laid out character by character in a forced left-to-right row,
 * otherwise a Persian (RTL) layout would mirror the row and "SSH" would read "HSS".
 * Text with RTL script swaps as a whole so Persian letters keep their joined shapes.
 */
@Composable
fun RollingText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    if (text.any { it.isRtlScript() }) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (slideInVertically(spring(dampingRatio = 0.82f, stiffness = 380f)) { it } + fadeIn()) togetherWith
                    (slideOutVertically { -it } + fadeOut())
            },
            label = "rollingWhole",
            modifier = modifier
        ) { value -> Text(value, style = style, maxLines = 1) }
        return
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier = modifier) {
            text.forEachIndexed { index, char ->
                key(index) {
                    AnimatedContent(
                        targetState = char,
                        transitionSpec = {
                            val up = targetState > initialState
                            (slideInVertically(spring(dampingRatio = 0.82f, stiffness = 380f)) { if (up) it else -it } + fadeIn()) togetherWith
                                (slideOutVertically { if (up) -it else it } + fadeOut())
                        },
                        label = "rolling"
                    ) { c -> Text(c.toString(), style = style, maxLines = 1) }
                }
            }
        }
    }
}

private fun Char.isRtlScript(): Boolean {
    val d = Character.getDirectionality(this)
    return d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
}

@Composable
private fun ControlChips(
    protocolSelection: String,
    conduitAllowed: Boolean,
    onProtocolSelected: (String) -> Unit,
    selectedRegionCode: String,
    onRegionClick: () -> Unit,
    pingText: String,
    pingEnabled: Boolean,
    pingRefreshing: Boolean,
    onRefreshPing: () -> Unit
) {
    var protocolMenu by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            DashboardChip(
                title = stringResource(R.string.azadi_protocol),
                value = protocolName(protocolSelection),
                icon = Icons.Default.CallSplit,
                showsChevron = true,
                onClick = { protocolMenu = true },
                modifier = Modifier.testTag("dashboardProtocolMenu")
            )
            DropdownMenu(
                expanded = protocolMenu,
                onDismissRequest = { protocolMenu = false },
                modifier = Modifier.background(Color(0xFF1C2230))
            ) {
                listOf("auto", "direct", "cdn_fronting", "conduit").forEach { option ->
                    val enabled = option != "conduit" || conduitAllowed
                    DropdownMenuItem(
                        text = { Text(protocolName(option), color = if (enabled) Color.White else AppColors.SecondaryText) },
                        trailingIcon = {
                            if (option == protocolSelection) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.IranGreenBright)
                            }
                        },
                        enabled = enabled,
                        onClick = {
                            protocolMenu = false
                            onProtocolSelected(option)
                        }
                    )
                }
            }
        }
        DashboardChip(
            title = stringResource(R.string.dashboard_region),
            value = if (selectedRegionCode.isEmpty() || selectedRegionCode == PsiphonConstants.REGION_CODE_ANY) {
                stringResource(R.string.region2_best_title)
            } else {
                regionDisplayName(selectedRegionCode)
            },
            icon = Icons.Default.Public,
            showsChevron = true,
            onClick = onRegionClick,
            modifier = Modifier
                .weight(1f)
                .testTag("dashboardRegionMenu")
        )
        DashboardChip(
            title = stringResource(R.string.dash2_ping),
            value = pingText,
            icon = Icons.Default.NetworkPing,
            trailing = Icons.Default.Refresh,
            trailingSpinning = pingRefreshing,
            enabled = pingEnabled,
            onClick = onRefreshPing,
            modifier = Modifier
                .weight(1f)
                .testTag("pingRefreshButton")
        )
    }
}

@Composable
private fun protocolName(option: String): String = when (option) {
    "direct" -> stringResource(R.string.azadi_protocol_direct)
    "cdn_fronting" -> stringResource(R.string.azadi_protocol_cdn)
    "conduit" -> stringResource(R.string.azadi_protocol_conduit)
    else -> stringResource(R.string.azadi_protocol_auto)
}

@Composable
private fun DashboardChip(
    title: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showsChevron: Boolean = false,
    trailing: ImageVector? = null,
    trailingSpinning: Boolean = false,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(16.dp)
    var spin by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(trailingSpinning) {
        if (trailingSpinning) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { spin = ((it - start) / 1_000_000f / 800f * 360f) % 360f }
        } else spin = 0f
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, AppColors.CardStroke, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = AppColors.SecondaryText, modifier = Modifier.size(11.dp))
            FitText(title, color = AppColors.SecondaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FitText(
                value,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (showsChevron) {
                Icon(Icons.Default.UnfoldMore, contentDescription = null, tint = AppColors.IranGreen, modifier = Modifier.size(14.dp))
            }
            if (trailing != null) {
                Icon(
                    trailing,
                    contentDescription = null,
                    tint = if (enabled) AppColors.IranGreen else AppColors.SecondaryText.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(spin)
                )
            }
        }
    }
}

@Composable
private fun TrafficRow(stats: TrafficStats) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(vertical = 10.dp)
    ) {
        TrafficMeter(
            stringResource(R.string.dashboard_download), stats.downloadSpeed, stats.totalDownload,
            Icons.Default.ArrowDownward, AppColors.IranGreen, "downloadSpeedLabel", Modifier.weight(1f)
        )
        Box(Modifier.size(width = 1.dp, height = 34.dp).background(AppColors.CardStroke))
        TrafficMeter(
            stringResource(R.string.dashboard_upload), stats.uploadSpeed, stats.totalUpload,
            Icons.Default.ArrowUpward, AppColors.IranRed, "uploadSpeedLabel", Modifier.weight(1f)
        )
    }
}

@Composable
private fun TrafficMeter(
    title: String,
    speed: String,
    total: String,
    icon: ImageVector,
    tint: Color,
    tag: String,
    modifier: Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.padding(horizontal = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f))
        ) {
            Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(16.dp))
        }
        Column {
            RollingText(
                speed,
                TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.testTag(tag)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FitText(stringResource(R.string.dash2_total) + " ", color = AppColors.SecondaryText, fontSize = 11.sp, maxLines = 1)
                RollingText(total, TextStyle(color = AppColors.SecondaryText, fontSize = 11.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
private fun DashboardSection(
    title: String,
    icon: ImageVector,
    tint: Color = AppColors.IranGreen,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, color = AppColors.SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(100.dp))
        Text(value, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CopyRow(label: String, value: String, context: Context) {
    val copied = stringResource(R.string.dash2_copied)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AppColors.SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(100.dp))
        Text(value, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        if (value.isNotBlank() && value != "—") {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.dash2_copy),
                tint = AppColors.IranGreen,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    }
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun DashboardNotice(
    icon: ImageVector,
    tint: Color,
    title: String?,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tint.copy(alpha = 0.18f))
            .border(1.dp, tint.copy(alpha = 0.3f), shape)
            .padding(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (title != null) Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(message, color = AppColors.SecondaryText, fontSize = 12.sp)
            if (actionLabel != null && onAction != null) {
                Text(
                    actionLabel,
                    color = AppColors.IranGreenBright,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable(onClick = onAction)
                )
            }
        }
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
