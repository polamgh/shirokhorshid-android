package com.psiphon3.ui.redesign

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * Text that never ends in "…": when it doesn't fit in [maxLines] it shrinks its font
 * (down to [minScale] of the requested size) until the whole string is visible, like
 * SwiftUI's `minimumScaleFactor`.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    minScale: Float = 0.5f
) {
    val base = style.merge(
        TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
            textAlign = textAlign ?: TextAlign.Unspecified
        )
    )
    val baseSize = if (base.fontSize.isSpecified) base.fontSize else 14.sp
    var scale by remember(text, base, maxLines) { mutableFloatStateOf(1f) }
    var settled by remember(text, base, maxLines) { mutableStateOf(false) }
    Text(
        text = text,
        modifier = modifier.drawWithContent { if (settled) drawContent() },
        style = base.copy(
            fontSize = baseSize * scale,
            lineHeight = if (base.lineHeight.isSpecified) base.lineHeight * scale else TextUnit.Unspecified,
            letterSpacing = if (base.letterSpacing.isSpecified) base.letterSpacing * scale else TextUnit.Unspecified
        ),
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Clip,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && scale > minScale) {
                scale = max(minScale, scale * 0.92f)
            } else {
                settled = true
            }
        }
    )
}
