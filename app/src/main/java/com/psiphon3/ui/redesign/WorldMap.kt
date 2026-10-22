package com.psiphon3.ui.redesign

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psiphon3.ui.theme.AppColors
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

data class MapCoordinate(val latitude: Double, val longitude: Double) {
    val isValid: Boolean get() = latitude in -90.0..90.0 && longitude in -180.0..180.0
}

object WorldMapLocations {
    /** Offline fallback for every region the app exposes, used until the IP lookup returns. */
    private val regionCentroids = mapOf(
        "US" to MapCoordinate(39.8, -98.6), "CA" to MapCoordinate(56.1, -106.3),
        "GB" to MapCoordinate(54.4, -2.5), "DE" to MapCoordinate(51.2, 10.4),
        "FR" to MapCoordinate(46.2, 2.2), "NL" to MapCoordinate(52.1, 5.3),
        "CH" to MapCoordinate(46.8, 8.2), "SE" to MapCoordinate(60.1, 18.6),
        "JP" to MapCoordinate(36.2, 138.3), "SG" to MapCoordinate(1.35, 103.8),
        "AU" to MapCoordinate(-25.3, 133.8), "IR" to MapCoordinate(32.4, 53.7),
        "AE" to MapCoordinate(23.4, 53.8), "IN" to MapCoordinate(20.6, 79.0),
        "BR" to MapCoordinate(-14.2, -51.9), "ZA" to MapCoordinate(-30.6, 22.9),
        "AT" to MapCoordinate(47.5, 14.5), "BE" to MapCoordinate(50.5, 4.5),
        "BG" to MapCoordinate(42.7, 25.5), "CZ" to MapCoordinate(49.8, 15.5),
        "DK" to MapCoordinate(56.0, 10.0), "EE" to MapCoordinate(58.6, 25.0),
        "ES" to MapCoordinate(40.4, -3.7), "FI" to MapCoordinate(61.9, 25.7),
        "GR" to MapCoordinate(39.1, 21.8), "HK" to MapCoordinate(22.3, 114.2),
        "HR" to MapCoordinate(45.1, 15.2), "HU" to MapCoordinate(47.2, 19.5),
        "ID" to MapCoordinate(-0.8, 113.9), "IE" to MapCoordinate(53.4, -8.2),
        "IS" to MapCoordinate(64.9, -19.0), "IT" to MapCoordinate(41.9, 12.6),
        "KE" to MapCoordinate(-0.02, 37.9), "KR" to MapCoordinate(35.9, 127.8),
        "LT" to MapCoordinate(55.2, 23.9), "LV" to MapCoordinate(56.9, 24.6),
        "MX" to MapCoordinate(23.6, -102.6), "MY" to MapCoordinate(4.2, 102.0),
        "NO" to MapCoordinate(60.5, 8.5), "NZ" to MapCoordinate(-40.9, 174.9),
        "PL" to MapCoordinate(51.9, 19.1), "PT" to MapCoordinate(39.4, -8.2),
        "RO" to MapCoordinate(45.9, 25.0), "RS" to MapCoordinate(44.0, 21.0),
        "SK" to MapCoordinate(48.7, 19.7), "TW" to MapCoordinate(23.7, 121.0),
        "UA" to MapCoordinate(48.4, 31.2), "AR" to MapCoordinate(-38.4, -63.6),
        "CL" to MapCoordinate(-35.7, -71.5), "CO" to MapCoordinate(4.6, -74.3)
    )

    fun centroid(countryCode: String?): MapCoordinate? =
        countryCode?.trim()?.uppercase()?.let { regionCentroids[it] }

    fun flagEmoji(countryCode: String?): String? {
        val code = countryCode?.trim()?.uppercase() ?: return null
        if (code.length != 2 || !code.all { it in 'A'..'Z' }) return null
        return code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    }
}

/**
 * Miller cylindrical projection, cropped to 80°N…58°S: less polar stretching than
 * Mercator and no Antarctica, which reads as a calmer, "atlas" silhouette.
 */
object MillerProjection {
    private const val NORTH = 80.0
    private const val SOUTH = -58.0
    private fun y(latitude: Double): Double = 1.25 * ln(tan(PI / 4 + 0.4 * latitude * PI / 180))
    private val yTop = y(NORTH)
    private val yBottom = y(SOUTH)
    val aspect: Float = ((yTop - yBottom) / (2 * PI)).toFloat()

    fun normalized(c: MapCoordinate): Offset {
        val lon = c.longitude.coerceIn(-180.0, 180.0)
        val lat = c.latitude.coerceIn(SOUTH, NORTH)
        return Offset(((lon + 180) / 360).toFloat(), ((yTop - y(lat)) / (yTop - yBottom)).toFloat())
    }

    @Suppress("unused")
    fun latitude(normalizedY: Double): Double {
        val v = yTop - normalizedY * (yTop - yBottom)
        return (atan(exp(v / 1.25)) - PI / 4) / 0.4 * 180 / PI
    }
}

/**
 * Dot-matrix land mask (hex-packed rows, odd rows shifted half a cell), generated from the
 * same simplified coastlines as the iOS app so both platforms draw an identical map.
 */
object WorldLandMask {
    const val COLUMNS = 150
    private val packed = arrayOf(
        "000000000ff9ffffe000f80000000000000000",
        "000000001fe7ffffe000f00000000000000000",
        "000000000fc3ffffe000700000000600000000",
        "000000000781ffffe000000000007f80000000",
        "0000000006001fffe00000000800ffc0000000",
        "0000018000001fffc00000002003ffd0000000",
        "000001fe00780fffc0000000203fffff040000",
        "0040007e007c0fff80000000c1ffffffff8000",
        "01fe031f003e07ff80001f0001fffffffffe60",
        "07fffff81c1e07fe00007fc03ffffffffffff8",
        "e7fffffffe0f87fc00007ff7fffffffffffffc",
        "e7fffffffe0f07e07801fffffffffffffffff8",
        "01fffffffc0f07c03001f7fffffffffffffff8",
        "03fffffff00403800007dffffffffffffffff8",
        "03fffffff01801800007cffffffffffffffbf0",
        "03e0ffffe03d00000007c7ffffffffffff6400",
        "00601ffff01f80000040cffffffffffffc0e00",
        "01001ffffc3f800000829ffffffffffff80c00",
        "00000fffffbfe00001a33ffffffffffff00c00",
        "000007ffffffe0000367fffffffffffffc0800",
        "000003ffffffd000006ffffffffffffffe0000",
        "000003fffffe3000003ffffffffffffff80000",
        "000001ffffff0800003ffffffffffffffa0000",
        "000001ffffff8000003ffebcffffffffe00000",
        "000001fffffc000001f1be0effffffffc30000",
        "000001fffff8000001e09ede7fffffff000000",
        "000001fffff0000001e20bfe7ffffff1040000",
        "000000ffffe0000001c283fe7ffffff9180000",
        "0000007fffe0000000ff003ffffffff8380000",
        "0000003fff80000001ff007ffffffff8400000",
        "0000000fff80000001fffffffffffffc000000",
        "0000000fc080000003ffff7efffffff8000000",
        "00000007e040000007ffffbf1ffffff8000000",
        "0000000bc00000000fffffbfe1fffff0000000",
        "00000001c06000000fffffdff07e7fc0000000",
        "00000001c40800000fffffdfe07c7e00000000",
        "000000007c0000000fffffefc0383f04000000",
        "000000000f0000001fffffee00701f04000000",
        "00000000010000000ffffff800300f80000000",
        "00000000011f800007ffffff00300200000000",
        "00000000003fc00003ffffff00080803000000",
        "00000000003ff00001c7fffe00000830000000",
        "00000000003ffc000001fffc00000478000000",
        "00000000007ffc000003fff8000008f4000000",
        "00000000003fff000001fff000000664200000",
        "00000000007fffe00001ffe00000060c1e0000",
        "00000000007ffff00000ffe000000180078000",
        "00000000003ffff00000ffe000000000008000",
        "00000000001fffe000007ff000000000200000",
        "00000000001fffc00000ffe200000000600000",
        "00000000000fffe00000ffe300000003fb0000",
        "000000000007ffc00000ff8600000007ff0000",
        "000000000003ffc000007fc60000000fff8000",
        "000000000007ff000000ff840000003fff8000",
        "000000000003fe0000007f800000003fffe000",
        "000000000007fc0000007f000000003fffc000",
        "000000000007fc0000003f000000001fffe000",
        "000000000007f80000003e000000001e3fc000",
        "000000000007f800000018000000001c0fc000",
        "00000000000fe00000000000000000000f8000",
        "00000000000fc0000000000000000000000008",
        "00000000000f80000000000000000000000010",
        "00000000000f00000000000000000000000020",
        "00000000000e00000000000000000000000040",
        "00000000000e00000000000000000000000000",
        "00000000001e00000000000000000000000000",
        "00000000000c00000000000000000000000000",
        "00000000001c00000000000000000000000000",
        "00000000000600000000000000000000000000",
        "00000000000000000000000000000000000000",
        "00000000000000000000000000000000000000"
    )
    val rows: Int = packed.size

    private val cells: BooleanArray = BooleanArray(rows * COLUMNS).also { cells ->
        packed.forEachIndexed { row, hex ->
            for (column in 0 until COLUMNS) {
                val nibble = Character.digit(hex[column / 4], 16)
                cells[row * COLUMNS + column] = (nibble shr (3 - column % 4)) and 1 == 1
            }
        }
    }

    fun isLand(row: Int, column: Int): Boolean = cells[row * COLUMNS + column]
}

private val OriginTint = Color(0xFFDBE6F5)
private val DotColor = Color(0xFFA8C2E6).copy(alpha = 0.36f)
private val GridColor = Color.White.copy(alpha = 0.06f)
private val Accent = AppColors.IranGreenBright

private fun mapRect(size: Size): Rect {
    var width = size.width * 1.12f
    if (width * MillerProjection.aspect > size.height * 0.94f) {
        width = size.height * 0.94f / MillerProjection.aspect
    }
    val height = width * MillerProjection.aspect
    return Rect(Offset((size.width - width) / 2, (size.height - height) / 2), Size(width, height))
}

private fun project(c: MapCoordinate, rect: Rect): Offset {
    val n = MillerProjection.normalized(c)
    return Offset(rect.left + n.x * rect.width, rect.top + n.y * rect.height)
}

/**
 * Fixed, privacy-friendly vector backdrop. It never downloads map tiles; only the already
 * fetched IP coordinates place the origin, the egress and the animated route between them.
 */
@Composable
fun VpnWorldMap(
    origin: MapCoordinate?,
    destination: MapCoordinate?,
    originCountryCode: String?,
    destinationCountryCode: String?,
    isConnected: Boolean,
    isConnecting: Boolean,
    modifier: Modifier = Modifier
) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { time = (it - start) / 1_000_000_000f }
        }
    }
    val routeAlpha by animateFloatAsState(if (isConnected && destination != null && origin != null) 1f else 0f, tween(450), label = "route")
    val shownDestination = if (isConnected) destination else null

    // Geography is not text: under RTL (Persian) offsets would mirror and put markers on the
    // wrong continent, so the map always lays out left-to-right.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier = modifier.clearAndSetSemantics { }) {
            val density = LocalDensity.current
            val size = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
            val rect = mapRect(size)
            val originPoint = origin?.takeIf { it.isValid }?.let { project(it, rect) }
            val destinationPoint = shownDestination?.takeIf { it.isValid }?.let { project(it, rect) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithCache {
                        val r = mapRect(this.size)
                        val o = origin?.takeIf { it.isValid }?.let { project(it, r) }
                        val d = shownDestination?.takeIf { it.isValid }?.let { project(it, r) }
                        val horizontal = Brush.horizontalGradient(
                            0f to Color.Transparent, 0.10f to Color.Black, 0.90f to Color.Black, 1f to Color.Transparent
                        )
                        val vertical = Brush.verticalGradient(
                            0f to Color.Transparent, 0.14f to Color.Black, 0.86f to Color.Black, 1f to Color.Transparent
                        )
                        onDrawBehind {
                            drawGraticule(r)
                            drawLand(r, o, d)
                            drawRect(horizontal, blendMode = BlendMode.DstIn)
                            drawRect(vertical, blendMode = BlendMode.DstIn)
                        }
                    }
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                if (originPoint != null && destinationPoint != null && routeAlpha > 0f) {
                    drawRoute(originPoint, destinationPoint, time, routeAlpha)
                }
                if (originPoint != null) {
                    drawOriginMarker(originPoint, time, searching = isConnecting && !isConnected)
                }
                if (destinationPoint != null) {
                    drawDestinationMarker(destinationPoint, time)
                }
            }

            if (originPoint != null) {
                MapLabel(originCountryCode, OriginTint, originPoint, liftPx = with(density) { 24.dp.toPx() })
            }
            if (destinationPoint != null) {
                MapLabel(destinationCountryCode, Accent, destinationPoint, liftPx = with(density) { 26.dp.toPx() })
            }
        }
    }
}

@Composable
private fun MapLabel(countryCode: String?, tint: Color, anchor: Offset, liftPx: Float) {
    val flag = WorldMapLocations.flagEmoji(countryCode) ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .wrapContentSize(unbounded = true)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(0, 0) {
                    placeable.place(
                        IntOffset(
                            (anchor.x - placeable.width / 2f).roundToInt(),
                            (anchor.y - liftPx - placeable.height / 2f).roundToInt()
                        )
                    )
                }
            }
            .background(Color(0xFF151B27).copy(alpha = 0.82f), CircleShape)
            .border(0.75.dp, tint.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(flag, fontSize = 12.sp)
        Text(
            countryCode!!.trim().uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = Color.White,
            modifier = Modifier.offset(x = 4.dp)
        )
    }
}

private fun DrawScope.drawGraticule(rect: Rect) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 4.dp.toPx()))
    val stroke = 0.6.dp.toPx()
    var longitude = -150.0
    while (longitude <= 150.0) {
        val x = project(MapCoordinate(0.0, longitude), rect).x
        drawLine(GridColor, Offset(x, rect.top), Offset(x, rect.bottom), stroke, pathEffect = dash)
        longitude += 30.0
    }
    for (latitude in listOf(-30.0, 0.0, 30.0, 60.0)) {
        val y = project(MapCoordinate(latitude, 0.0), rect).y
        drawLine(GridColor, Offset(rect.left, y), Offset(rect.right, y), stroke, pathEffect = dash)
    }
}

private fun glowWeight(point: Offset, center: Offset, radius: Float): Float? {
    val distance = hypot(point.x - center.x, point.y - center.y)
    if (distance >= radius) return null
    val t = 1 - distance / radius
    return t * t
}

private fun DrawScope.drawLand(rect: Rect, origin: Offset?, destination: Offset?) {
    val columns = WorldLandMask.COLUMNS
    val rows = WorldLandMask.rows
    val spacingX = rect.width / columns
    val spacingY = rect.height / rows
    val radius = min(spacingX, spacingY) * 0.54f / 2
    val glowRadius = spacingX * 9
    for (row in 0 until rows) {
        val offset = if (row % 2 == 0) 0f else 0.5f
        for (column in 0 until columns) {
            if (!WorldLandMask.isLand(row, column)) continue
            val center = Offset(
                rect.left + (column + 0.5f + offset) * spacingX,
                rect.top + (row + 0.5f) * spacingY
            )
            val destinationWeight = destination?.let { glowWeight(center, it, glowRadius) }
            if (destinationWeight != null) {
                drawCircle(
                    Accent.copy(alpha = 0.22f + 0.68f * destinationWeight),
                    radius * (1 + 0.25f * destinationWeight),
                    center
                )
                continue
            }
            val originWeight = origin?.let { glowWeight(center, it, glowRadius * 0.8f) }
            if (originWeight != null) {
                drawCircle(OriginTint.copy(alpha = 0.22f + 0.5f * originWeight), radius, center)
            } else {
                drawCircle(DotColor, radius, center)
            }
        }
    }
}

private fun quad(start: Offset, control: Offset, end: Offset, t: Float): Offset {
    val u = 1 - t
    return Offset(
        u * u * start.x + 2 * u * t * control.x + t * t * end.x,
        u * u * start.y + 2 * u * t * control.y + t * t * end.y
    )
}

private fun DrawScope.drawRoute(start: Offset, end: Offset, time: Float, alpha: Float) {
    val mid = Offset((start.x + end.x) / 2, (start.y + end.y) / 2)
    val distance = hypot(end.x - start.x, end.y - start.y)
    val lift = min(110.dp.toPx(), max(26.dp.toPx(), distance * 0.32f))
    val control = Offset(mid.x, mid.y - lift)
    val route = Path().apply {
        moveTo(start.x, start.y)
        quadraticBezierTo(control.x, control.y, end.x, end.y)
    }
    val gradient = Brush.linearGradient(listOf(OriginTint, Accent), start, end)
    drawPath(route, gradient, alpha = 0.18f * alpha, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
    drawPath(route, gradient, alpha = 0.9f * alpha, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))

    // Comet: a bright head travelling origin → egress with a fading tail.
    val progress = (time % 2.4f) / 2.4f
    val segments = 14
    val segmentLength = 0.014f
    for (index in 0 until segments) {
        val to = progress - index * segmentLength
        val from = to - segmentLength
        if (to <= 0f) break
        val fade = 1f - index.toFloat() / segments
        drawLine(
            Accent.copy(alpha = 0.85f * fade * alpha),
            quad(start, control, end, max(0f, from)),
            quad(start, control, end, to),
            strokeWidth = (2.6f * fade + 0.6f).dp.toPx(),
            cap = StrokeCap.Round
        )
    }
    if (progress > 0.002f) {
        val head = quad(start, control, end, progress)
        drawCircle(Accent.copy(alpha = 0.25f * alpha), 7.dp.toPx(), head)
        drawCircle(Color.White.copy(alpha = alpha), 2.6.dp.toPx(), head)
    }
}

private fun DrawScope.drawOriginMarker(center: Offset, time: Float, searching: Boolean) {
    if (searching) {
        val phase = (time % 1.6f) / 1.6f
        for (ring in 0 until 2) {
            val local = (phase + ring * 0.5f) % 1f
            drawCircle(
                OriginTint.copy(alpha = 0.6f * (1 - local)),
                (10 + 54 * local).dp.toPx() / 2,
                center,
                style = Stroke(1.2.dp.toPx())
            )
        }
    }
    drawCircle(OriginTint.copy(alpha = 0.14f), 11.dp.toPx(), center)
    drawCircle(OriginTint.copy(alpha = 0.35f), 7.dp.toPx(), center)
    drawCircle(Color.White, 4.5.dp.toPx(), center)
    drawCircle(OriginTint, 4.5.dp.toPx(), center, style = Stroke(2.dp.toPx()))
}

private fun DrawScope.drawDestinationMarker(center: Offset, time: Float) {
    val phase = (time % 2f) / 2f
    drawCircle(
        Accent.copy(alpha = 0.7f * (1 - phase)),
        (14 + 40 * phase).dp.toPx() / 2,
        center,
        style = Stroke(1.5.dp.toPx())
    )
    drawCircle(Accent.copy(alpha = 0.22f), 13.dp.toPx(), center)
    drawCircle(Accent.copy(alpha = 0.45f), 9.dp.toPx(), center)
    drawCircle(Accent, 5.5.dp.toPx(), center)
    drawCircle(Color.White, 5.5.dp.toPx(), center, style = Stroke(2.dp.toPx()))
}
