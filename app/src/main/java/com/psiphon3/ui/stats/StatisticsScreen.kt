package com.psiphon3.ui.stats

import com.psiphon3.ui.redesign.FitText

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psiphon3.R
import com.psiphon3.azadi.ConnectedTunnelProtocolParser
import com.psiphon3.azadi.StatsHistoryStore
import com.psiphon3.azadi.StatsLinkState
import com.psiphon3.azadi.StatsProtocolShare
import com.psiphon3.azadi.StatsSessionRecord
import com.psiphon3.azadi.StatsSnapshot
import com.psiphon3.ui.theme.AppColors
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private val CardStroke = Color.White.copy(alpha = 0.10f)
private val CardFill = Color.White.copy(alpha = 0.06f)

private fun textStyle(size: Int, weight: FontWeight = FontWeight.Normal, mono: Boolean = false) = TextStyle(
    fontSize = size.sp,
    lineHeight = (size * 1.3f).sp,
    fontWeight = weight,
    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
)

/**
 * The Stats tab (port of iOS StatisticsView). Reads [StatsHistoryStore.flow]; the activity
 * must drive the store (tick + lifecycle calls). [bottomPadding] keeps the last card clear of
 * the floating bottom nav.
 */
@Composable
fun StatisticsScreen(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 96.dp
) {
    val snapshot by StatsHistoryStore.flow.collectAsState()
    StatisticsContent(snapshot = snapshot, modifier = modifier, bottomPadding = bottomPadding)
}

/** Stateless body of [StatisticsScreen], for previews or a custom snapshot source. */
@Composable
fun StatisticsContent(
    snapshot: StatsSnapshot,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 96.dp
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppColors.SurfaceTop, AppColors.SurfaceBottom)))
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(AppColors.IranGreenBright.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = 420.dp.toPx()
                    )
                )
            }
            .testTag("statisticsScreen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.stats_title),
                color = AppColors.PrimaryText,
                style = textStyle(28, FontWeight.Bold),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .semantics { heading() }
            )
            Appear(0, appeared) { StatusPill(snapshot) }
            Appear(1, appeared) { LiveSpeedCard(snapshot) }
            Appear(2, appeared) { SessionTiles(snapshot) }
            if (snapshot.latencySamples.isNotEmpty()) {
                Appear(3, appeared) { LatencyCard(snapshot) }
            }
            Appear(4, appeared) { WeeklyCard(snapshot) }
            Appear(5, appeared) { ConnectionsCard(snapshot) }
            val breakdown = snapshot.protocolBreakdown
            if (breakdown.isNotEmpty()) {
                Appear(6, appeared) { ProtocolsCard(breakdown.take(5), appeared) }
            }
            Appear(7, appeared) { RecentCard(snapshot) }
            Appear(8, appeared) { AllTimeCard(snapshot) }
        }
    }
}

// region Sections

@Composable
private fun StatusPill(snapshot: StatsSnapshot) {
    val tint = when (snapshot.linkState) {
        StatsLinkState.CONNECTED -> AppColors.IranGreenBright
        StatsLinkState.CONNECTING -> AppColors.IranWhite.copy(alpha = 0.9f)
        StatsLinkState.FAILED -> AppColors.IranRed
        StatsLinkState.DISCONNECTED -> Color.White.copy(alpha = 0.5f)
    }
    val label = when (snapshot.linkState) {
        StatsLinkState.CONNECTED -> stringResource(R.string.stats_status_connected)
        StatsLinkState.CONNECTING -> stringResource(R.string.stats_status_connecting)
        StatsLinkState.FAILED -> stringResource(R.string.stats_status_failed)
        StatsLinkState.DISCONNECTED -> stringResource(R.string.stats_status_disconnected)
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(AppColors.Glass)
            .border(1.dp, CardStroke, CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .drawBehind {
                    if (snapshot.isConnected) {
                        drawCircle(tint.copy(alpha = 0.35f), radius = size.minDimension)
                    }
                    drawCircle(tint)
                }
        )
        Text(label, color = AppColors.PrimaryText, style = textStyle(13, FontWeight.SemiBold))
        if (snapshot.isConnected) {
            Text("·", color = AppColors.SecondaryText, style = textStyle(13))
            Text(
                StatsFormat.duration(snapshot.currentSessionDurationMs),
                color = AppColors.SecondaryText,
                style = textStyle(13, mono = true)
            )
        }
    }
}

@Composable
private fun LiveSpeedCard(snapshot: StatsSnapshot) {
    val latest = snapshot.latestSpeed
    StatsCard(
        title = stringResource(R.string.stats_live_speed),
        icon = Icons.Filled.GraphicEq,
        trailing = stringResource(R.string.stats_last_two_minutes)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SpeedReadout(
                icon = Icons.Filled.ArrowDownward,
                tint = AppColors.IranGreenBright,
                title = stringResource(R.string.stats_download),
                value = StatsFormat.speed(latest?.down ?: 0L)
            )
            SpeedReadout(
                icon = Icons.Filled.ArrowUpward,
                tint = AppColors.IranRed,
                title = stringResource(R.string.stats_upload),
                value = StatsFormat.speed(latest?.up ?: 0L)
            )
        }
        SpeedAreaChart(
            samples = snapshot.speedSamples,
            scaleMax = snapshot.speedChartScale,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(150.dp)
        )
    }
}

@Composable
private fun SpeedReadout(icon: ImageVector, tint: Color, title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Text(title, color = AppColors.SecondaryText, style = textStyle(12, FontWeight.SemiBold))
        }
        FitText(value, color = AppColors.PrimaryText, style = textStyle(22, FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun SessionTiles(snapshot: StatsSnapshot) {
    val connected = snapshot.isConnected
    val down = snapshot.currentSessionDown
    val up = snapshot.currentSessionUp
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatsTile(
                icon = Icons.Filled.Schedule,
                tint = StatsPalette.Blue,
                title = stringResource(R.string.stats_session_time),
                value = if (connected) StatsFormat.duration(snapshot.currentSessionDurationMs) else "—",
                modifier = Modifier.weight(1f)
            )
            StatsTile(
                icon = Icons.Filled.SwapVert,
                tint = AppColors.IranGreen,
                title = stringResource(R.string.stats_session_data),
                value = if (connected) StatsFormat.total(down + up) else "—",
                detail = if (connected) "↓ ${StatsFormat.total(down)}  ↑ ${StatsFormat.total(up)}" else null,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatsTile(
                icon = Icons.Filled.Speed,
                tint = StatsPalette.Orange,
                title = stringResource(R.string.stats_peak_speed),
                value = StatsFormat.speed(snapshot.sessionPeakDown),
                detail = "↑ ${StatsFormat.speed(snapshot.sessionPeakUp)}",
                modifier = Modifier.weight(1f)
            )
            StatsTile(
                icon = Icons.Filled.WifiTethering,
                tint = StatsPalette.Purple,
                title = stringResource(R.string.stats_avg_ping),
                value = StatsFormat.millis(snapshot.averageLatency),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LatencyCard(snapshot: StatsSnapshot) {
    val values = snapshot.latencySamples.map { it.ms }
    StatsCard(title = stringResource(R.string.stats_latency), icon = Icons.Filled.Timer) {
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            MiniStat(stringResource(R.string.stats_now), StatsFormat.millis(values.lastOrNull()), StatsPalette.Orange)
            MiniStat(stringResource(R.string.stats_min), StatsFormat.millis(values.minOrNull()), AppColors.IranGreenBright)
            MiniStat(stringResource(R.string.stats_avg), StatsFormat.millis(snapshot.averageLatency), StatsPalette.Blue)
        }
        LatencyLineChart(
            samples = snapshot.latencySamples,
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
        )
    }
}

@Composable
private fun MiniStat(title: String, value: String, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = AppColors.SecondaryText, style = textStyle(11, FontWeight.SemiBold))
        FitText(value, color = tint, style = textStyle(15, FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun WeeklyCard(snapshot: StatsSnapshot) {
    val week = remember(snapshot.dailyUsage, snapshot.nowMs / 60_000L) { snapshot.lastSevenDays() }
    val total = week.sumOf { it.total }
    val configuration = LocalConfiguration.current
    val locale: Locale = remember(configuration) {
        if (configuration.locales.isEmpty()) Locale.getDefault() else configuration.locales[0]
    }
    StatsCard(title = stringResource(R.string.stats_daily_usage), icon = Icons.Filled.BarChart) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(StatsFormat.total(total), color = AppColors.PrimaryText, style = textStyle(22, FontWeight.Bold))
            Text(
                stringResource(R.string.stats_this_week),
                color = AppColors.SecondaryText,
                style = textStyle(12),
                modifier = Modifier.padding(bottom = 3.dp)
            )
            Spacer(Modifier.weight(1f))
            LegendDot(AppColors.IranGreenBright, stringResource(R.string.stats_download))
            LegendDot(AppColors.IranRed, stringResource(R.string.stats_upload))
        }
        WeeklyUsageBars(
            days = week,
            locale = locale,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        modifier = Modifier.padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, color = AppColors.SecondaryText, style = textStyle(11))
    }
}

@Composable
private fun ConnectionsCard(snapshot: StatsSnapshot) {
    StatsCard(title = stringResource(R.string.stats_connections), icon = Icons.Filled.Hub) {
        val rate = snapshot.successRate
        if (rate == null) {
            EmptyHint()
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RatioRing(ratio = rate, modifier = Modifier.size(92.dp))
                Text(
                    stringResource(R.string.stats_success_rate),
                    color = AppColors.SecondaryText,
                    style = textStyle(11, FontWeight.SemiBold)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConnectionFact(Icons.Filled.Tag, stringResource(R.string.stats_total_sessions), snapshot.sessions.size.toString())
                ConnectionFact(
                    Icons.Filled.Bolt,
                    stringResource(R.string.stats_avg_connect),
                    snapshot.averageTimeToConnectMs?.let { String.format(Locale.US, "%.0f s", it / 1000.0) } ?: "—"
                )
                ConnectionFact(
                    Icons.Filled.Shield,
                    stringResource(R.string.stats_protected_time),
                    protectedTimeText(snapshot.totalProtectedTimeMs)
                )
            }
        }
    }
}

private fun protectedTimeText(totalMs: Long): String {
    val total = totalMs / 1000L
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
private fun ConnectionFact(icon: ImageVector, title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            icon,
            contentDescription = null,
            tint = AppColors.IranGreenBright,
            modifier = Modifier.size(16.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = AppColors.SecondaryText, style = textStyle(11))
            Text(value, color = AppColors.PrimaryText, style = textStyle(15, FontWeight.SemiBold))
        }
    }
}

@Composable
private fun ProtocolsCard(breakdown: List<StatsProtocolShare>, appeared: Boolean) {
    val total = breakdown.sumOf { it.count }.coerceAtLeast(1)
    val palette = listOf(AppColors.IranGreenBright, StatsPalette.Blue, StatsPalette.Orange, StatsPalette.Purple, StatsPalette.Pink)
    StatsCard(title = stringResource(R.string.stats_protocols), icon = Icons.Filled.Route) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            breakdown.forEachIndexed { index, item ->
                val share = item.count.toFloat() / total
                val color = palette[index % palette.size]
                val fill by animateFloatAsState(
                    targetValue = if (appeared) share else 0f,
                    animationSpec = tween(durationMillis = 800, delayMillis = 200 + index * 80, easing = FastOutSlowInEasing),
                    label = "protocolShare"
                )
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FitText(
                            item.name,
                            color = AppColors.PrimaryText,
                            style = textStyle(13, FontWeight.SemiBold),
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${(share * 100).roundToInt()}%  ·  ${item.count}",
                            color = AppColors.SecondaryText,
                            style = textStyle(12)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(AppColors.PrimaryText.copy(alpha = 0.07f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fill.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentCard(snapshot: StatsSnapshot) {
    StatsCard(title = stringResource(R.string.stats_recent), icon = Icons.Filled.History) {
        if (snapshot.sessions.isEmpty()) {
            EmptyHint()
        } else Column {
            snapshot.sessions.take(6).forEachIndexed { index, session ->
                if (index > 0) HorizontalDivider(color = CardStroke, thickness = 1.dp)
                SessionRow(session, snapshot.nowMs)
            }
        }
    }
}

@Composable
private fun SessionRow(session: StatsSessionRecord, nowMs: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AppColors.PrimaryText.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (session.succeeded) flagEmoji(session.countryCode) else "⚠️",
                style = textStyle(18)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FitText(
                text = if (session.succeeded) {
                    ConnectedTunnelProtocolParser.displayName(session.tunnelProtocol).ifBlank { "—" }
                } else {
                    stringResource(R.string.stats_failed)
                },
                color = if (session.succeeded) AppColors.PrimaryText else AppColors.IranRed,
                style = textStyle(15, FontWeight.SemiBold),
                maxLines = 1)
            Text(
                text = relativeTime(session.attemptStartedAt, nowMs),
                color = AppColors.SecondaryText,
                style = textStyle(11)
            )
        }
        if (session.succeeded) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    StatsFormat.duration(session.durationMs),
                    color = AppColors.PrimaryText,
                    style = textStyle(12, FontWeight.SemiBold, mono = true)
                )
                Text(
                    StatsFormat.total(session.bytesDown + session.bytesUp),
                    color = AppColors.SecondaryText,
                    style = textStyle(11)
                )
            }
        }
    }
}

@Composable
private fun relativeTime(timeMs: Long, nowMs: Long): String {
    val seconds = ((nowMs - timeMs) / 1000L).coerceAtLeast(0L)
    return when {
        seconds < 60 -> stringResource(R.string.stats_rel_just_now)
        seconds < 3600 -> stringResource(R.string.stats_rel_minutes, (seconds / 60).toInt())
        seconds < 86_400 -> stringResource(R.string.stats_rel_hours, (seconds / 3600).toInt())
        else -> stringResource(R.string.stats_rel_days, (seconds / 86_400).toInt())
    }
}

private fun flagEmoji(countryCode: String): String {
    val code = countryCode.trim().uppercase(Locale.US)
    if (code.length != 2 || !code.all { it in 'A'..'Z' }) return "🌐"
    val first = Character.codePointAt(code, 0) - 'A'.code + 0x1F1E6
    val second = Character.codePointAt(code, 1) - 'A'.code + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

@Composable
private fun AllTimeCard(snapshot: StatsSnapshot) {
    StatsCard(title = stringResource(R.string.stats_all_time), icon = Icons.Filled.AllInclusive) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TotalBlock(
                icon = Icons.Filled.ArrowCircleDown,
                tint = AppColors.IranGreenBright,
                title = stringResource(R.string.stats_total_download),
                value = snapshot.allTimeDown,
                modifier = Modifier.weight(1f)
            )
            TotalBlock(
                icon = Icons.Filled.ArrowCircleUp,
                tint = AppColors.IranRed,
                title = stringResource(R.string.stats_total_upload),
                value = snapshot.allTimeUp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TotalBlock(icon: ImageVector, tint: Color, title: String, value: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.PrimaryText.copy(alpha = 0.05f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            FitText(
                title,
                color = AppColors.SecondaryText,
                style = textStyle(11),
                maxLines = 1)
            Text(StatsFormat.total(value), color = AppColors.PrimaryText, style = textStyle(17, FontWeight.SemiBold), maxLines = 1)
        }
    }
}

@Composable
private fun EmptyHint() {
    Text(
        text = stringResource(R.string.stats_no_data),
        color = AppColors.SecondaryText,
        style = textStyle(13),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    )
}

// endregion

// region Building blocks

@Composable
private fun StatsCard(
    title: String,
    icon: ImageVector,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardFill)
            .border(1.dp, CardStroke, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AppColors.IranGreenBright, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            FitText(
                title,
                color = AppColors.PrimaryText,
                style = textStyle(15, FontWeight.SemiBold),
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            if (trailing != null) {
                Text(trailing, color = AppColors.SecondaryText, style = textStyle(11))
            }
        }
        content()
    }
}

@Composable
private fun StatsTile(
    icon: ImageVector,
    tint: Color,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 126.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardFill)
            .border(1.dp, CardStroke, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.92f), tint))),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        FitText(
            title,
            color = AppColors.SecondaryText,
            style = textStyle(12, FontWeight.SemiBold),
            maxLines = 1)
        FitText(
            value,
            color = AppColors.PrimaryText,
            style = textStyle(20, FontWeight.Bold),
            maxLines = 1)
        if (detail != null) {
            FitText(
                detail,
                color = AppColors.SecondaryText,
                style = textStyle(11),
                maxLines = 1)
        }
    }
}

/** Staggered fade + rise on first appearance (iOS StatsAppear). */
@Composable
private fun Appear(index: Int, appeared: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(appeared) {
        if (appeared) {
            delay(index * 50L)
            progress.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow))
        }
    }
    Box(
        modifier = Modifier.graphicsLayer {
            val p = progress.value
            alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * 18.dp.toPx()
        }
    ) {
        content()
    }
}

// endregion
