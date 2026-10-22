package com.psiphon3.ui.redesign

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.psiphon3.ui.VpnConnectionStatus
import com.psiphon3.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

private enum class PowerMode { IDLE, BUSY, CONNECTED, ERROR }

private fun VpnConnectionStatus.powerMode(): PowerMode = when (this) {
    VpnConnectionStatus.CONNECTED -> PowerMode.CONNECTED
    VpnConnectionStatus.CONNECTING,
    VpnConnectionStatus.WAITING_FOR_NETWORK,
    VpnConnectionStatus.WAITING -> PowerMode.BUSY
    VpnConnectionStatus.ERROR -> PowerMode.ERROR
    VpnConnectionStatus.DISCONNECTED -> PowerMode.IDLE
}

private const val DESIGN = 220f
private const val BURST_SECONDS = 1.1f

/**
 * Animated power disc: a slow sheen around a hairline ring while idle, counter-rotating
 * comets with in-spiralling particles while connecting, calm ripples and sparkles once
 * connected, plus a one-shot shockwave the moment the tunnel comes up. Effects are drawn in
 * a 220dp design space and scaled to [size].
 */
@Composable
fun AzadiPowerButton(
    status: VpnConnectionStatus,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 124.dp,
    contentDescription: String = "",
    stateDescription: String = ""
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val mode = status.powerMode()
    val energy = if (mode == PowerMode.CONNECTED || mode == PowerMode.BUSY) AppColors.IranGreenBright else AppColors.IranRed

    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { time = (it - start) / 1_000_000_000f }
        }
    }

    var burstStart by remember { mutableStateOf<Float?>(null) }
    var previous by remember { mutableStateOf(mode) }
    LaunchedEffect(mode) {
        if (mode == PowerMode.CONNECTED && previous != PowerMode.CONNECTED) {
            burstStart = time
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(((BURST_SECONDS + 0.1f) * 1000).toLong())
            burstStart = null
        }
        previous = mode
    }

    val press = remember { Animatable(1f) }
    val scale = size.value / DESIGN

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .alpha(if (isEnabled) 1f else 0.42f)
            .graphicsLayer {
                scaleX = press.value
                scaleY = press.value
            }
            .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = stateDescription
            }
    ) {
        // Effects spill past the disc, so the canvas is larger than the button itself.
        Canvas(modifier = Modifier.requiredSize(size * (320f / DESIGN))) {
            val center = Offset(this.size.width / 2, this.size.height / 2)
            scale(scale, pivot = center) {
                val t = time
                drawAura(center, t, mode, energy)
                when (mode) {
                    PowerMode.BUSY -> drawVortex(center, t)
                    PowerMode.CONNECTED -> drawConnected(center, t)
                    PowerMode.IDLE -> drawIdle(center, t, energy, speed = 0.35f, alpha = 0.55f)
                    PowerMode.ERROR -> drawIdle(center, t, energy, speed = 0.9f, alpha = 0.8f)
                }
                burstStart?.let { drawBurst(center, t - it) }
            }
        }

        val discSize = size * (132f / DESIGN)
        val discColors = when (mode) {
            PowerMode.CONNECTED -> listOf(AppColors.IranGreenBright, AppColors.IranGreen)
            PowerMode.BUSY -> listOf(AppColors.IranWhite.copy(alpha = 0.95f), Color(0xFFBFC7CC))
            PowerMode.ERROR -> listOf(AppColors.IranRed, AppColors.IranRedDeep)
            PowerMode.IDLE -> listOf(AppColors.IranRed.copy(alpha = 0.85f), AppColors.IranRedDeep)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(discSize)
                .shadow(
                    elevation = 18.dp * scale,
                    shape = CircleShape,
                    ambientColor = energy.copy(alpha = 0.5f),
                    spotColor = energy.copy(alpha = if (mode == PowerMode.CONNECTED) 0.8f else 0.6f)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = isEnabled,
                    role = Role.Button
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch {
                        press.animateTo(0.9f, spring(dampingRatio = 0.55f, stiffness = 900f))
                        press.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 300f))
                    }
                    onClick()
                }
        ) {
            Canvas(modifier = Modifier.size(discSize)) {
                val c = Offset(this.size.width / 2, this.size.height / 2)
                val r = this.size.minDimension / 2
                drawCircle(Brush.linearGradient(discColors, Offset.Zero, Offset(this.size.width, this.size.height)), r, c)
                // Specular sheen sweeping around the disc (fast while busy).
                rotate(degrees = (time * (if (mode == PowerMode.BUSY) 3.2f else 0.5f) * 180f / PI.toFloat()), pivot = c) {
                    drawCircle(
                        Brush.sweepGradient(
                            0f to Color.White.copy(alpha = 0f),
                            0.08f to Color.White.copy(alpha = if (mode == PowerMode.BUSY) 0.45f else 0.22f),
                            0.22f to Color.White.copy(alpha = 0f),
                            1f to Color.White.copy(alpha = 0f),
                            center = c
                        ),
                        r,
                        c
                    )
                }
                drawCircle(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f), Color.Black.copy(alpha = 0.18f))
                    ),
                    r,
                    c
                )
                val ring = 2.dp.toPx() * scale
                drawCircle(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.18f)),
                        Offset.Zero,
                        Offset(this.size.width, this.size.height)
                    ),
                    r - ring / 2,
                    c,
                    style = Stroke(ring)
                )
                drawCircle(energy.copy(alpha = 0.35f), r - 9.dp.toPx() * scale, c, style = Stroke(1.dp.toPx() * scale))
            }
            val pulse = if (mode == PowerMode.BUSY) 0.55f + 0.45f * (sin(time * 5f) * 0.5f + 0.5f) else 1f
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = null,
                tint = if (mode == PowerMode.BUSY) AppColors.IranGreen else Color.White,
                modifier = Modifier
                    .size(size * (54f / DESIGN))
                    .alpha(pulse)
            )
        }
    }
}

private fun circlePoint(center: Offset, radius: Float, angle: Float) =
    Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))

private fun DrawScope.drawAura(center: Offset, time: Float, mode: PowerMode, energy: Color) {
    val (speed, strength) = when (mode) {
        PowerMode.CONNECTED -> 1.4f to 0.42f
        PowerMode.BUSY -> 4.2f to 0.36f
        PowerMode.ERROR -> 3.0f to 0.34f
        PowerMode.IDLE -> 1.1f to 0.24f
    }
    val breath = sin(time * speed) * 0.5f + 0.5f
    val radius = 124.dp.toPx() * (0.94f + 0.08f * breath)
    drawCircle(
        Brush.radialGradient(
            0f to energy.copy(alpha = strength + 0.14f * breath),
            0.24f to energy.copy(alpha = strength + 0.14f * breath),
            0.7f to energy.copy(alpha = 0.06f),
            1f to Color.Transparent,
            center = center,
            radius = radius
        ),
        radius,
        center
    )
}

/** Idle: a slow sheen sweeping around a hairline ring, plus a breathing outline. */
private fun DrawScope.drawIdle(center: Offset, time: Float, energy: Color, speed: Float, alpha: Float) {
    val degrees = (time * speed * 360f) % 360f
    rotate(degrees, pivot = center) {
        drawCircle(
            Brush.sweepGradient(
                0f to energy.copy(alpha = 0f),
                0.18f to energy.copy(alpha = 0.9f * alpha),
                0.24f to Color.White.copy(alpha = 0.7f * alpha),
                0.5f to energy.copy(alpha = 0f),
                1f to energy.copy(alpha = 0f),
                center = center
            ),
            88.dp.toPx(),
            center,
            style = Stroke(2.4.dp.toPx())
        )
    }
    val breath = sin(time * 1.6f) * 0.5f + 0.5f
    drawCircle(
        energy.copy(alpha = 0.10f + 0.14f * breath),
        (96 + 3 * breath).dp.toPx(),
        center,
        style = Stroke(1.4.dp.toPx())
    )
}

/** Connecting: two counter-rotating comets and particles spiralling into the disc. */
private fun DrawScope.drawVortex(center: Offset, time: Float) {
    data class Comet(val radius: Float, val speed: Float, val color: Color, val width: Float)
    val comets = listOf(
        Comet(82f, 2.8f, AppColors.IranGreenBright, 4.2f),
        Comet(98f, -2.0f, Color.White, 3.0f)
    )
    for (comet in comets) {
        val radius = comet.radius.dp.toPx()
        val head = time * comet.speed
        val segments = 26
        val direction = if (comet.speed > 0) -1f else 1f
        for (index in 0 until segments) {
            val fade = 1f - index.toFloat() / segments
            val start = head + direction * index * 0.055f
            val end = start + direction * 0.06f
            val from = minOf(start, end)
            val sweep = abs(end - start)
            drawArc(
                color = comet.color.copy(alpha = 0.95f * fade * fade),
                startAngle = from * 180f / PI.toFloat(),
                sweepAngle = sweep * 180f / PI.toFloat(),
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke((comet.width * fade + 0.4f).dp.toPx(), cap = StrokeCap.Round)
            )
        }
        val headPoint = circlePoint(center, radius, head)
        drawCircle(comet.color.copy(alpha = 0.25f), 7.dp.toPx(), headPoint)
        drawCircle(Color.White, 2.6.dp.toPx(), headPoint)
    }
    val particles = 16
    for (index in 0 until particles) {
        val seed = index.toFloat() / particles
        val phase = (time * 0.55f + seed) % 1f
        val radius = (126 - phase * 58).dp.toPx()
        val angle = seed * 2 * PI.toFloat() + time * (0.6f + (index % 3) * 0.25f) + phase * 2.4f
        val alpha = sin(phase * PI.toFloat())
        val color = if (index % 2 == 0) AppColors.IranGreenBright else Color.White
        drawCircle(color.copy(alpha = 0.85f * alpha), (1.2f + 1.6f * alpha).dp.toPx(), circlePoint(center, radius, angle))
    }
}

/** Connected: calm ripples, a rotating halo and twinkling sparkles. */
private fun DrawScope.drawConnected(center: Offset, time: Float) {
    for (ripple in 0 until 3) {
        val phase = (time / 2.8f + ripple / 3f) % 1f
        drawCircle(
            AppColors.IranGreenBright.copy(alpha = 0.42f * (1 - phase)),
            (72 + phase * 62).dp.toPx(),
            center,
            style = Stroke((1.8f * (1 - phase) + 0.4f).dp.toPx())
        )
    }
    rotate((time * 0.9f * 180f / PI.toFloat()) % 360f, pivot = center) {
        drawCircle(
            Brush.sweepGradient(
                0f to AppColors.IranGreenBright.copy(alpha = 0.95f),
                0.12f to Color.White.copy(alpha = 0.75f),
                0.4f to AppColors.IranGreenBright.copy(alpha = 0.25f),
                0.7f to AppColors.IranGreen.copy(alpha = 0.05f),
                1f to AppColors.IranGreenBright.copy(alpha = 0.95f),
                center = center
            ),
            88.dp.toPx(),
            center,
            style = Stroke(2.6.dp.toPx())
        )
    }
    for (index in 0 until 10) {
        val seed = index.toFloat()
        val angle = seed * 0.628f + time * (if (index % 2 == 0) 0.35f else -0.25f)
        val radius = (100 + 8 * sin(time * 0.8f + seed)).dp.toPx()
        val twinkle = abs(sin(time * 2.2f + seed * 1.7f))
        drawCircle(
            Color.White.copy(alpha = 0.25f + 0.65f * twinkle),
            (0.8f + 1.4f * twinkle).dp.toPx(),
            circlePoint(center, radius, angle)
        )
    }
}

/** One-shot shockwave and radial particle burst when the tunnel is established. */
private fun DrawScope.drawBurst(center: Offset, elapsed: Float) {
    if (elapsed < 0f || elapsed >= BURST_SECONDS) return
    val progress = elapsed / BURST_SECONDS
    val eased = 1f - (1f - progress).pow(3)
    val fade = 1f - progress
    drawCircle(
        AppColors.IranGreenBright.copy(alpha = 0.9f * fade),
        (66 + eased * 92).dp.toPx(),
        center,
        style = Stroke((5 * fade + 0.5f).dp.toPx())
    )
    drawCircle(
        Color.White.copy(alpha = 0.7f * fade),
        (66 + eased * 64).dp.toPx(),
        center,
        style = Stroke((2.5f * fade + 0.3f).dp.toPx())
    )
    for (index in 0 until 24) {
        val angle = index / 24f * 2 * PI.toFloat() + (index % 2) * 0.13f
        val reach = 66 + eased * (if (index % 3 == 0) 104 else 82)
        val color = if (index % 2 == 0) AppColors.IranGreenBright else Color.White
        drawCircle(color.copy(alpha = fade), (2.8f * fade + 0.4f).dp.toPx(), circlePoint(center, reach.dp.toPx(), angle))
    }
}
