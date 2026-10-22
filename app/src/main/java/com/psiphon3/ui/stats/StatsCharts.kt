package com.psiphon3.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psiphon3.azadi.StatsDailyUsage
import com.psiphon3.azadi.StatsHistoryStore
import com.psiphon3.azadi.StatsLatencySample
import com.psiphon3.azadi.StatsSpeedSample
import com.psiphon3.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Hand-drawn charts for the Stats tab (port of iOS StatsCharts.swift). Charts are always
// laid out left-to-right (time flows left to right) even in Persian.

internal object StatsPalette {
    val Blue = Color(0xFF0A84FF)
    val Orange = Color(0xFFFF9F0A)
    val Purple = Color(0xFFBF5AF2)
    val Pink = Color(0xFFFF375F)
}

/** Same formatting as iOS ByteCountFormatter (Latin digits, fixed units). */
internal object StatsFormat {
    fun total(bytes: Long): String {
        val d = bytes.toDouble()
        return when {
            d >= 1_073_741_824.0 -> String.format(Locale.US, "%.2f GB", d / 1_073_741_824.0)
            d >= 1_048_576.0 -> String.format(Locale.US, "%.2f MB", d / 1_048_576.0)
            d >= 1024.0 -> String.format(Locale.US, "%.0f KB", d / 1024.0)
            bytes <= 0L -> "0 KB"
            else -> "$bytes B"
        }
    }

    fun speed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0L) return "0 KB/s"
        val d = bytesPerSecond.toDouble()
        return when {
            d >= 1_000_000.0 -> String.format(Locale.US, "%.1f MB/s", d / 1_000_000.0)
            d >= 1_000.0 -> String.format(Locale.US, "%.1f KB/s", d / 1_000.0)
            else -> String.format(Locale.US, "%.0f B/s", d)
        }
    }

    fun duration(ms: Long): String {
        val t = max(0L, ms / 1000L)
        return String.format(Locale.US, "%02d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
    }

    fun millis(ms: Int?): String = ms?.let { "$it ms" } ?: "—"
}

internal object ChartPath {
    /** Smooth curve through the points (Catmull-Rom converted to cubic Béziers). */
    fun smoothLine(points: List<Offset>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points[0].x, points[0].y)
        if (points.size < 2) return path
        for (index in 0 until points.size - 1) {
            val p0 = points[max(index - 1, 0)]
            val p1 = points[index]
            val p2 = points[index + 1]
            val p3 = points[min(index + 2, points.size - 1)]
            path.cubicTo(
                p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                p2.x, p2.y
            )
        }
        return path
    }

    fun area(points: List<Offset>, baseline: Float): Path {
        val path = smoothLine(points)
        if (points.size < 2) return path
        path.lineTo(points.last().x, baseline)
        path.lineTo(points.first().x, baseline)
        path.close()
        return path
    }
}

@Composable
private fun ForceLtr(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

/**
 * Download as a glowing filled area, upload as a thinner line, three dashed guide lines with
 * speed labels on the right. [scaleMax] is the store's eased ceiling, so the scale also
 * shrinks when speed drops.
 */
@Composable
fun SpeedAreaChart(
    samples: List<StatsSpeedSample>,
    scaleMax: Double,
    modifier: Modifier = Modifier
) {
    val animatedScale by animateFloatAsState(
        targetValue = scaleMax.toFloat().coerceAtLeast(1f),
        animationSpec = tween(durationMillis = 700),
        label = "speedScale"
    )
    val density = LocalDensity.current
    ForceLtr {
        BoxWithConstraints(modifier = modifier.clearAndSetSemantics { }) {
            val labelWidth = 58.dp
            val topPad = 6.dp
            val plotWidth = maxWidth - labelWidth
            val plotHeight = maxHeight - 12.dp
            Canvas(
                modifier = Modifier
                    .width(plotWidth)
                    .fillMaxHeight()
            ) {
                val top = topPad.toPx()
                val h = plotHeight.toPx()
                val bottom = top + h
                val w = size.width
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()), 0f)
                for (line in 1..3) {
                    val y = bottom - h * line / 3f
                    drawLine(
                        color = AppColors.SecondaryText.copy(alpha = 0.18f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 0.6.dp.toPx(),
                        pathEffect = dash
                    )
                }
                if (samples.size < 2) return@Canvas
                val step = w / (samples.size - 1)
                fun pointsOf(value: (StatsSpeedSample) -> Long): List<Offset> = samples.mapIndexed { index, sample ->
                    val ratio = min(value(sample).toDouble() / animatedScale, 1.04).toFloat()
                    Offset(index * step, bottom - h * ratio)
                }
                val down = pointsOf { it.down }
                val up = pointsOf { it.up }
                val green = AppColors.IranGreenBright
                val red = AppColors.IranRed

                drawPath(
                    ChartPath.area(down, bottom),
                    brush = Brush.verticalGradient(
                        listOf(green.copy(alpha = 0.45f), green.copy(alpha = 0.02f)),
                        startY = top,
                        endY = bottom
                    )
                )
                val downLine = ChartPath.smoothLine(down)
                drawPath(
                    downLine,
                    color = green.copy(alpha = 0.22f),
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    downLine,
                    color = green,
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    ChartPath.area(up, bottom),
                    brush = Brush.verticalGradient(
                        listOf(red.copy(alpha = 0.22f), red.copy(alpha = 0f)),
                        startY = top,
                        endY = bottom
                    )
                )
                drawPath(
                    ChartPath.smoothLine(up),
                    color = red,
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                val head = down.last()
                drawCircle(green.copy(alpha = 0.3f), radius = 8.dp.toPx(), center = head)
                drawCircle(green, radius = 4.dp.toPx(), center = head)
                drawCircle(Color.White, radius = 4.dp.toPx(), center = head, style = Stroke(width = 1.5.dp.toPx()))
            }
            // Guide labels, vertically centred on their lines.
            for (line in 1..3) {
                val fraction = line / 3f
                val lineY = topPad + plotHeight * (1f - fraction)
                val labelHeightPx = with(density) { 12.sp.toPx() }
                Text(
                    text = StatsFormat.speed((animatedScale * fraction).toLong()),
                    color = AppColors.SecondaryText,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .width(labelWidth - 6.dp)
                        .offset {
                            IntOffset(
                                x = (plotWidth + 6.dp).roundToPx(),
                                y = (lineY.toPx() - labelHeightPx / 2f).roundToInt()
                            )
                        }
                )
            }
        }
    }
}

/** Ping over time: a smooth line with small markers. */
@Composable
fun LatencyLineChart(
    samples: List<StatsLatencySample>,
    modifier: Modifier = Modifier,
    tint: Color = StatsPalette.Orange
) {
    ForceLtr {
        Canvas(modifier = modifier.clearAndSetSemantics { }) {
            if (samples.size < 2) {
                samples.firstOrNull()?.let {
                    drawCircle(tint, radius = 3.5.dp.toPx(), center = Offset(size.width / 2f, size.height / 2f))
                }
                return@Canvas
            }
            val values = samples.map { it.ms }
            val low = (values.minOrNull() ?: 0).toDouble()
            val high = (values.maxOrNull() ?: 1).toDouble()
            val span = max(high - low, 20.0)
            val left = 4.dp.toPx()
            val top = 6.dp.toPx()
            val w = size.width - 8.dp.toPx()
            val h = size.height - 12.dp.toPx()
            val bottom = top + h
            val points = samples.mapIndexed { index, sample ->
                val ratio = ((sample.ms - low + span * 0.1) / (span * 1.2)).toFloat()
                Offset(left + w * index / (samples.size - 1), bottom - h * ratio)
            }
            drawPath(
                ChartPath.area(points, bottom),
                brush = Brush.verticalGradient(listOf(tint.copy(alpha = 0.28f), tint.copy(alpha = 0f)), startY = top, endY = bottom)
            )
            drawPath(
                ChartPath.smoothLine(points),
                color = tint,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            points.forEachIndexed { index, point ->
                val isLast = index == points.size - 1
                drawCircle(
                    color = if (isLast) tint else tint.copy(alpha = 0.55f),
                    radius = (if (isLast) 3.5.dp else 1.75.dp).toPx(),
                    center = point
                )
            }
        }
    }
}

/** Seven stacked day bars (download + upload) with weekday labels; today is highlighted. */
@Composable
fun WeeklyUsageBars(
    days: List<StatsDailyUsage>,
    locale: Locale,
    modifier: Modifier = Modifier
) {
    val grow = remember(days.size) { List(days.size) { Animatable(0f) } }
    LaunchedEffect(grow) {
        grow.forEachIndexed { index, animatable ->
            launch {
                delay(index * 50L)
                animatable.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow))
            }
        }
    }
    val labels = remember(days, locale) {
        val pattern = if (locale.language == "fa") "EEEEE" else "EEE"
        val formatter = SimpleDateFormat(pattern, locale)
        days.map { day -> StatsHistoryStore.dateForDay(day.day)?.let { formatter.format(Date(it)) } ?: "" }
    }
    val totals = days.map { it.total.toDouble() }
    val peak = max(totals.maxOrNull() ?: 0.0, 1.0)
    ForceLtr {
        Column(modifier = modifier.clearAndSetSemantics { }) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (days.isEmpty()) return@Canvas
                val gap = 10.dp.toPx()
                val barWidth = (size.width - gap * (days.size - 1)) / days.size
                val radius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                days.forEachIndexed { index, day ->
                    val isToday = index == days.size - 1
                    val total = totals[index]
                    val full = max(if (total > 0) 6.dp.toPx() else 3.dp.toPx(), (size.height * (total / peak)).toFloat())
                    val progress = grow.getOrNull(index)?.value ?: 1f
                    val barHeight = max(3.dp.toPx(), full * progress)
                    val left = index * (barWidth + gap)
                    val top = size.height - barHeight
                    if (isToday && total > 0) {
                        drawRoundRect(
                            color = AppColors.IranGreenBright.copy(alpha = 0.18f),
                            topLeft = Offset(left - 3.dp.toPx(), top - 3.dp.toPx()),
                            size = Size(barWidth + 6.dp.toPx(), barHeight + 3.dp.toPx()),
                            cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx())
                        )
                    }
                    val upShare = if (total > 0) (day.bytesUp.toDouble() / total).toFloat() else 0f
                    val upHeight = barHeight * upShare
                    val clip = Path().apply {
                        addRoundRect(RoundRect(left, top, left + barWidth, size.height, radius))
                    }
                    clipPath(clip) {
                        drawRect(
                            color = AppColors.IranRed.copy(alpha = if (isToday) 0.95f else 0.6f),
                            topLeft = Offset(left, top),
                            size = Size(barWidth, upHeight)
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(
                                    AppColors.IranGreenBright.copy(alpha = if (isToday) 1f else 0.65f),
                                    AppColors.IranGreen.copy(alpha = if (isToday) 1f else 0.55f)
                                ),
                                startY = top + upHeight,
                                endY = size.height
                            ),
                            topLeft = Offset(left, top + upHeight),
                            size = Size(barWidth, barHeight - upHeight)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                labels.forEachIndexed { index, label ->
                    val isToday = index == labels.size - 1
                    Text(
                        text = label,
                        color = if (isToday) AppColors.PrimaryText else AppColors.SecondaryText,
                        fontSize = 10.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Animated ring for a 0…1 ratio with the percentage in the middle. */
@Composable
fun RatioRing(
    ratio: Double,
    modifier: Modifier = Modifier
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(150)
        shown = true
    }
    val sweep by animateFloatAsState(
        targetValue = if (shown) ratio.toFloat().coerceIn(0f, 1f) else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessVeryLow),
        label = "ratioRing"
    )
    Box(modifier = modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2f + 3.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = AppColors.PrimaryText.copy(alpha = 0.08f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            if (sweep > 0.001f) {
                rotate(-90f) {
                    drawArc(
                        color = AppColors.IranGreenBright.copy(alpha = 0.22f),
                        startAngle = 0f,
                        sweepAngle = 360f * sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke + 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(AppColors.IranGreen, AppColors.IranGreenBright, AppColors.IranGreen),
                            center = center
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f * sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
        }
        Text(
            text = "${(ratio * 100).roundToInt()}%",
            color = AppColors.PrimaryText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
