package com.psiphon3.ui.redesign

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psiphon3.R
import com.psiphon3.ui.theme.AppColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Launch sequence in the dashboard's visual language: the dotted world fades in, the icon
 * springs up inside a flag-colored halo, the script wordmark writes itself, the flag rule
 * grows from the center, the lion takes one peek, then everything lifts away.
 */
@Composable
fun AzadiSplash(onFinished: () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val map = remember { Animatable(0f) }
    val icon = remember { Animatable(0f) }
    val halo = remember { Animatable(0f) }
    val write = remember { Animatable(0f) }
    val rule = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    val loader = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            listOf(map, icon, halo, write, rule, tagline).forEach { it.snapTo(1f) }
            delay(1200)
            onFinished()
            return@LaunchedEffect
        }
        coroutineScope {
            launch { map.animateTo(1f, tween(1600, easing = LinearOutSlowInEasing)) }
            delay(100)
            launch { icon.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 140f)) }
            launch { halo.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
            delay(350)
            launch { write.animateTo(1.05f, tween(950, easing = FastOutSlowInEasing)) }
            delay(600)
            launch { rule.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 120f)) }
            delay(200)
            launch { tagline.animateTo(1f, tween(500, easing = LinearOutSlowInEasing)) }
            launch {
                delay(200)
                loader.animateTo(1f, tween(300))
            }
            delay(1750)
            exit.animateTo(1f, tween(400))
        }
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppColors.SurfaceTop, AppColors.SurfaceBottom)))
            .graphicsLayer {
                val s = 1f + 0.08f * exit.value
                scaleX = s
                scaleY = s
                alpha = 1f - exit.value
            }
            .testTag("splashScreen"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.42f)
            drawCircle(
                Brush.radialGradient(
                    listOf(AppColors.IranGreenBright.copy(alpha = 0.18f * icon.value.coerceIn(0f, 1f)), Color.Transparent),
                    center = center,
                    radius = 380.dp.toPx()
                ),
                380.dp.toPx(),
                center
            )
        }

        VpnWorldMap(
            origin = null,
            destination = null,
            originCountryCode = null,
            destinationCountryCode = null,
            isConnected = false,
            isConnecting = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .offset(y = (-40).dp)
                .graphicsLayer {
                    val s = 1.12f - 0.12f * map.value
                    scaleX = s
                    scaleY = s
                    alpha = 0.75f * map.value
                }
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                Canvas(
                    Modifier
                        .size(156.dp)
                        .graphicsLayer {
                            val s = 0.85f + 0.15f * halo.value
                            scaleX = s
                            scaleY = s
                        }
                ) {
                    val stroke = 2.5.dp.toPx()
                    drawCircle(
                        AppColors.IranGreenBright.copy(alpha = 0.18f * halo.value),
                        size.minDimension / 2,
                        style = Stroke(14.dp.toPx())
                    )
                    rotate(-90f) {
                        drawArc(
                            Brush.sweepGradient(
                                listOf(AppColors.IranGreenBright, AppColors.IranWhite, AppColors.IranRed, AppColors.IranGreenBright)
                            ),
                            startAngle = 0f,
                            sweepAngle = 360f * halo.value,
                            useCenter = false,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(104.dp)
                        .graphicsLayer {
                            val s = 0.4f + 0.6f * icon.value
                            scaleX = s
                            scaleY = s
                            alpha = icon.value.coerceIn(0f, 1f)
                        }
                        .shadow(
                            24.dp,
                            RoundedCornerShape(24.dp),
                            ambientColor = AppColors.IranGreenBright.copy(alpha = 0.45f),
                            spotColor = AppColors.IranGreenBright.copy(alpha = 0.45f)
                        )
                        .clip(RoundedCornerShape(24.dp))
                )
            }

            AzadiWordmark(
                fontSize = 46.sp,
                color = Color.White,
                lionStartDelayMs = 1350,
                lionRepeats = false,
                lionMaxSpots = 1,
                lionExtraDrop = 6.dp,
                modifier = Modifier.padding(top = 6.dp),
                textModifier = Modifier
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        val p = write.value
                        val solid = (p - 0.04f).coerceIn(0f, 1f)
                        val clear = max(solid, (p + 0.04f).coerceIn(0f, 1f))
                        drawRect(
                            Brush.horizontalGradient(
                                0f to Color.Black,
                                solid to Color.Black,
                                clear to Color.Transparent,
                                1f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            )

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .width(150.dp)
                        .height(3.dp)
                        .graphicsLayer { scaleX = rule.value }
                        .clip(CircleShape)
                ) {
                    Box(Modifier.weight(1f).height(3.dp).background(AppColors.IranGreen))
                    Box(Modifier.weight(1f).height(3.dp).background(AppColors.IranWhite))
                    Box(Modifier.weight(1f).height(3.dp).background(AppColors.IranRed))
                }
            }

            Text(
                text = stringResource(R.string.dash2_splash_tagline),
                color = AppColors.SecondaryText,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 36.dp)
                    .padding(top = 16.dp)
                    .graphicsLayer {
                        alpha = tagline.value
                        translationY = (1f - tagline.value) * 10.dp.toPx()
                    }
            )
            Spacer(Modifier.weight(1f))
            SplashLoader(
                Modifier
                    .padding(bottom = 54.dp)
                    .graphicsLayer { alpha = loader.value }
            )
        }
    }
}

/** Three flag-colored dots bouncing in sequence. */
@Composable
private fun SplashLoader(modifier: Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1_000_000_000f }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = modifier) {
            listOf(AppColors.IranGreenBright, AppColors.IranWhite, AppColors.IranRed).forEachIndexed { index, color ->
                Box(
                    Modifier
                        .size(7.dp)
                        .graphicsLayer {
                            val phase = ((time * 1.6f - index * 0.18f) % 1f + 1f) % 1f
                            val lift = max(0f, sin(phase * 2 * PI.toFloat()))
                            translationY = -7.dp.toPx() * lift
                            alpha = 0.55f + 0.45f * lift
                        }
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}
