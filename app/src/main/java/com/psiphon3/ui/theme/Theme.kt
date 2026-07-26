package com.psiphon3.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.psiphon3.R

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn, FontWeight.Normal),
    Font(R.font.vazirmatn, FontWeight.Medium),
    Font(R.font.vazirmatn, FontWeight.Bold)
)

val AzadiTypography = Typography()

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF2EB852),
    secondary = Color(0xFFD11F2E),
    background = Color.Black,
    surface = Color(0xFF1C1C1E),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    error = Color(0xFFD11F2E)
)

@Composable
fun AzadiTunnelTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AzadiTypography,
        content = content
    )
}

object AppColors {
    val IranGreen = Color(0xFF1F9938)
    val IranGreenBright = Color(0xFF2EB852)
    val IranRed = Color(0xFFD11F2E)
    val IranRedDeep = Color(0xFF9E1424)
    val IranWhite = Color(0xFFFAFAFA)

    val Primary = IranGreenBright
    val Secondary = IranGreenBright
    val Danger = IranRed
    val Background = Color(0xFF000000)
    val CardBackground = Color(0xFF1C1C1E)
    val CardBackgroundElevated = Color(0xFF242426)
    val CardStroke = Color.White.copy(alpha = 0.08f)
    val SubtitleText = Color.White.copy(alpha = 0.55f)

    val ConnectedGreen = IranGreenBright
    val DisconnectedRed = IranRed
    val NavBlue = Color(0xFF0A84FF)
    val GlowGreen = Color(0xFF30D158)
}

object AppGradients {
    fun connected(): Brush = Brush.linearGradient(
        colors = listOf(Color(0xFF34C759), Color(0xFF248A3D))
    )

    fun disconnected(): Brush = Brush.linearGradient(
        colors = listOf(Color(0xFFE53935), Color(0xFFB71C1C))
    )

    fun connecting(): Brush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE5E5EA),
            Color(0xFFAEAEB2)
        )
    )
}
