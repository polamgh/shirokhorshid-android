package com.psiphon3.ui.redesign

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.psiphon3.ui.theme.GrandHotel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Script "AzadiTunnel" wordmark in one ink color with a small lion living behind the
 * letters. [fontSize] 32.sp matches the dashboard header, 46.sp the splash.
 */
@Composable
fun AzadiWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 32.sp,
    color: Color = Color.White,
    showLion: Boolean = true,
    lionStartDelayMs: Long = 1500,
    lionRepeats: Boolean = true,
    lionMaxSpots: Int = 3,
    lionExtraDrop: Dp = 0.dp,
    textModifier: Modifier = Modifier
) {
    // Geography-like decoration: the lion's track and the Latin wordmark always run LTR.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        var textWidthPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        Box(
            modifier = modifier.semantics {
                contentDescription = "AzadiTunnel"
                heading()
            }
        ) {
            if (showLion && textWidthPx > 0) {
                PeekingLion(
                    trackWidth = with(density) { textWidthPx.toDp() },
                    startDelayMs = lionStartDelayMs,
                    repeats = lionRepeats,
                    maxSpots = lionMaxSpots,
                    modifier = Modifier.offset(
                        y = -PeekingLionMetrics.WindowHeight + PeekingLionMetrics.HideLine + lionExtraDrop
                    )
                )
            }
            Text(
                text = "AzadiTunnel",
                style = TextStyle(fontFamily = GrandHotel, fontSize = fontSize, color = color),
                maxLines = 1,
                modifier = textModifier
                    .clearAndSetSemantics { }
                    .onSizeChanged { textWidthPx = it.width }
            )
        }
    }
}

object PeekingLionMetrics {
    /** Clip window above the hide line; the line sits inside the letters. */
    val WindowHeight = 42.dp
    val HideLine = 21.dp
    val LionSize = 26.dp
}

private enum class LionPose(val topDp: Float) {
    HIDDEN(44f), EARS(33f), PEEK(17f), HOP(8f)
}

/**
 * A small lion that pops up at random spots, breathes and glances around, ducks to its
 * ear tips to scurry behind the letters to another spot, sometimes hops, then hides for a
 * few seconds. Skipped when system animations are turned off.
 */
@Composable
fun PeekingLion(
    trackWidth: Dp,
    modifier: Modifier = Modifier,
    startDelayMs: Long = 1500,
    repeats: Boolean = true,
    maxSpots: Int = 3
) {
    val context = LocalContext.current
    val animationsOff = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val top = remember { Animatable(LionPose.HIDDEN.topDp) }
    val position = remember { Animatable(0.7f) }
    val tilt = remember { Animatable(0f) }
    var peeking by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf(0f) }

    LaunchedEffect(peeking) {
        if (!peeking) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { time = (it - start) / 1_000_000_000f }
        }
    }

    LaunchedEffect(trackWidth, animationsOff) {
        if (animationsOff) return@LaunchedEffect
        val popSpring = spring<Float>(dampingRatio = 0.5f, stiffness = 260f)

        suspend fun popUp() {
            peeking = true
            launch { tilt.animateTo(0f, tween(200)) }
            top.animateTo(LionPose.PEEK.topDp, popSpring)
            if (Random.nextBoolean()) {
                top.animateTo(LionPose.HOP.topDp, tween(160, easing = FastOutSlowInEasing))
                top.animateTo(LionPose.PEEK.topDp, spring(dampingRatio = 0.45f, stiffness = 380f))
            }
            repeat(Random.nextInt(1, 4)) {
                tilt.animateTo(Random.nextDouble(-16.0, 16.0).toFloat(), tween(260))
                delay(Random.nextLong(350, 700) - 260)
            }
            tilt.animateTo(0f, tween(200))
            delay(250)
        }

        delay(startDelayMs)
        while (isActive) {
            val count = if (maxSpots <= 1) 1 else Random.nextInt(2, maxSpots + 1)
            val spots = MutableList(count) { Random.nextDouble(0.05, 0.95).toFloat() }
            position.snapTo(spots.removeAt(0))
            popUp()
            for (spot in spots) {
                peeking = false
                top.animateTo(LionPose.EARS.topDp, tween(220, easing = LinearEasing))
                val lean = if (spot > position.value) 14f else -14f
                val travelMs = ((abs(spot - position.value) * 1.1f + 0.35f) * 1000).toInt()
                launch { tilt.animateTo(lean, tween(travelMs, easing = FastOutSlowInEasing)) }
                position.animateTo(spot, tween(travelMs, easing = FastOutSlowInEasing))
                popUp()
            }
            peeking = false
            launch { tilt.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) }
            top.animateTo(LionPose.HIDDEN.topDp, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
            if (!repeats) return@LaunchedEffect
            delay(Random.nextLong(2500, 5000))
        }
    }

    val trackRoom = (trackWidth - PeekingLionMetrics.LionSize).coerceAtLeast(0.dp)
    val breathing = if (peeking) sin(time * 5.5f) else 0f
    Box(
        modifier = modifier
            .width(maxOf(trackWidth, PeekingLionMetrics.LionSize))
            .height(PeekingLionMetrics.WindowHeight)
            .clipToBounds()
            .clearAndSetSemantics { }
    ) {
        Text(
            text = "🦁",
            fontSize = 22.sp,
            modifier = Modifier
                .offset(x = trackRoom * position.value, y = (top.value + breathing).dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    rotationZ = tilt.value
                    scaleX = 1f - 0.02f * breathing
                    scaleY = 1f + 0.04f * breathing
                }
        )
    }
}
