package com.psiphon3.ui.azadi

import com.psiphon3.ui.redesign.FitText

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psiphon3.BuildConfig
import com.psiphon3.R
import com.psiphon3.ui.redesign.AzadiWordmark
import com.psiphon3.ui.theme.AppColors

val AzadiWarningOrange = Color(0xFFFF9500)
val AzadiActionBlue = Color(0xFF0A84FF)
val AzadiDestructiveRed = Color(0xFFFF453A)

/** iOS system tints (dark appearance) used by the colored settings icon tiles. */
object SettingsTints {
    val Gray = Color(0xFF8E8E93)
    val Blue = Color(0xFF0A84FF)
    val Teal = Color(0xFF40C8E0)
    val Orange = Color(0xFFFF9F0A)
    val Purple = Color(0xFFBF5AF2)
    val Red = Color(0xFFFF453A)
    val Indigo = Color(0xFF5E5CE6)
    val Pink = Color(0xFFFF375F)
    val Yellow = Color(0xFFFFBD29)
    val Graphite = Color(0xFF33333D)
}

/** Corner radius shared by every translucent settings card. */
val SettingsCardShape = RoundedCornerShape(20.dp)

/** Start inset of dividers under rows that lead with an icon tile (16 padding + 30 tile + 12 gap). */
val SettingsIconInset: Dp = 58.dp

private val RowTitleSize = 16.sp
private val NoteSize = 13.sp

// region Surfaces

/** Map-surface backdrop (same palette as the dashboard) with a soft green glow at the top. */
@Composable
fun AzadiSettingsSurface(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppColors.SurfaceTop, AppColors.SurfaceBottom)))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(AppColors.IranGreenBright.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = 420.dp.toPx()
                    )
                )
            },
        contentAlignment = contentAlignment,
        content = content
    )
}

/** Translucent glass card: [AppColors.Glass] fill, hairline [AppColors.GlassStroke] edge, 20dp corners. */
@Composable
fun AzadiGlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SettingsCardShape)
            .background(AppColors.Glass)
            .border(1.dp, AppColors.GlassStroke, SettingsCardShape)
            .padding(contentPadding),
        content = content
    )
}

/**
 * Grouped settings card. The default 6dp padding keeps free-form content (text, buttons)
 * used by the sub-screens off the edges; the Settings root passes a vertical-only padding.
 */
@Composable
fun AzadiSettingsGroup(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(6.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    AzadiGlassCard(modifier = modifier, contentPadding = contentPadding, content = content)
}

// endregion

// region Building blocks

/** Rounded, tinted icon tile in the style of iOS Settings. */
@Composable
fun SettingsIconTile(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp
) {
    val shape = RoundedCornerShape(size * 0.27f)
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 2.dp,
                shape = shape,
                ambientColor = tint.copy(alpha = 0.35f),
                spotColor = tint.copy(alpha = 0.35f)
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(tint, Color.White, 0.10f), tint))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.56f)
        )
    }
}

/** Icon tile + title (+ optional subtitle) used as the leading part of rows. */
@Composable
fun SettingsRowLabel(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = SettingsTints.Gray,
    subtitle: String? = null,
    titleColor: Color = AppColors.PrimaryText
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            SettingsIconTile(icon = icon, tint = tint)
            Spacer(Modifier.width(12.dp))
        }
        Column {
            Text(title, color = titleColor, fontSize = RowTitleSize, lineHeight = 21.sp)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                FitText(
                    text = subtitle,
                    color = AppColors.SecondaryText,
                    fontSize = NoteSize,
                    lineHeight = 18.sp,
                    maxLines = 3)
            }
        }
    }
}

/** Section header with a small green glyph, matching the dashboard's tone. */
@Composable
fun AzadiSectionHeader(title: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .padding(start = 6.dp, end = 6.dp, bottom = 8.dp, top = 18.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppColors.IranGreenBright,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = title,
            color = AppColors.SecondaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun AzadiDivider(startInset: Dp = 16.dp) {
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.08f),
        modifier = Modifier.padding(start = startInset, end = 16.dp)
    )
}

@Composable
private fun RowChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = AppColors.SecondaryText.copy(alpha = 0.7f),
        modifier = Modifier.size(20.dp)
    )
}

private fun Modifier.optionalTestTag(tag: String?): Modifier =
    if (tag != null) this.testTag(tag) else this

// endregion

// region Rows

@Composable
fun AzadiToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    warning: String? = null,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    icon: ImageVector? = null,
    tint: Color = SettingsTints.Gray
) {
    val noteStart = if (icon != null) 42.dp else 0.dp
    Column(
        modifier = modifier
            .optionalTestTag(testTag)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsRowLabel(
                title = label,
                icon = icon,
                tint = tint,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppColors.IranGreenBright,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.16f),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                color = AppColors.SecondaryText,
                fontSize = NoteSize,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = noteStart)
            )
        }
        if (!warning.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                warning,
                color = AppColors.Warning,
                fontSize = NoteSize,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = noteStart)
            )
        }
    }
}

/** Row that opens a picker sheet: label, current value and an up/down glyph (iOS menu picker). */
@Composable
fun AzadiPickerRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    tint: Color = SettingsTints.Gray,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    Row(
        modifier = modifier
            .optionalTestTag(testTag)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRowLabel(title = label, icon = icon, tint = tint, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        FitText(
            text = value,
            color = AppColors.SecondaryText,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 170.dp)
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = Icons.Filled.UnfoldMore,
            contentDescription = null,
            tint = AppColors.SecondaryText.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun AzadiValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = SettingsTints.Gray,
    monospaceValue: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRowLabel(title = label, icon = icon, tint = tint, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        FitText(
            text = value,
            color = AppColors.SecondaryText,
            fontSize = 15.sp,
            fontFamily = if (monospaceValue) FontFamily.Monospace else null,
            maxLines = 2,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}

@Composable
fun AzadiNavRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    icon: ImageVector? = null,
    tint: Color = SettingsTints.Gray,
    value: String? = null
) {
    Row(
        modifier = modifier
            .optionalTestTag(testTag)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRowLabel(
            title = title,
            subtitle = subtitle,
            icon = icon,
            tint = tint,
            modifier = Modifier.weight(1f)
        )
        if (!value.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            FitText(
                text = value,
                color = AppColors.SecondaryText,
                fontSize = 15.sp,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 160.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
        RowChevron()
    }
}

/** Tappable action row with an icon tile (iOS Settings "button" row). */
@Composable
fun AzadiActionRow(
    title: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    titleColor: Color = AppColors.PrimaryText
) {
    Row(
        modifier = modifier
            .optionalTestTag(testTag)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRowLabel(
            title = title,
            icon = icon,
            tint = tint,
            titleColor = titleColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun AzadiCopyRow(label: String, value: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AppColors.PrimaryText, fontSize = RowTitleSize, modifier = Modifier.weight(1f))
        Text(value, color = AppColors.SecondaryText, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun AzadiActionLink(
    text: String,
    color: Color = AzadiActionBlue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .optionalTestTag(testTag)
            .fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text, color = color, fontSize = RowTitleSize, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun AzadiOptionRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    Row(
        modifier = modifier
            .optionalTestTag(testTag)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = AppColors.PrimaryText, fontSize = RowTitleSize, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = AppColors.SecondaryText, fontSize = NoteSize, lineHeight = 18.sp)
            }
        }
        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (selected) AppColors.IranGreenBright else AppColors.SecondaryText.copy(alpha = 0.5f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun AzadiDisclosureGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title,
                color = AppColors.PrimaryText,
                fontSize = RowTitleSize,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (expanded) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = AppColors.SecondaryText
                )
            } else {
                RowChevron()
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                content = content
            )
        }
    }
}

/** Supporting copy under a row, kept quieter than the row itself. */
@Composable
fun AzadiFooterNote(text: String, color: Color = AppColors.SecondaryText) {
    Text(
        text = text,
        color = color,
        fontSize = NoteSize,
        lineHeight = 18.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
fun AzadiWarningBlock(text: String) {
    Text(
        text = text,
        color = AppColors.Warning,
        fontSize = NoteSize,
        lineHeight = 18.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

// endregion

// region Identity

/** App logo on the dark rounded tile used by the iOS app icon. */
@Composable
fun AzadiAppIconTile(size: Dp, modifier: Modifier = Modifier, withShadow: Boolean = true) {
    val shape = RoundedCornerShape(size * 0.2237f)
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (withShadow) {
                    Modifier.shadow(
                        elevation = size * 0.12f,
                        shape = shape,
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF2A2D33), Color(0xFF0B0C0F))
                )
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), shape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_app_logo),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(size * 0.1f)
        )
    }
}

/** Green / white / red hairline; always drawn left-to-right like the flag. */
@Composable
fun SettingsFlagStripe(modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .height(3.dp)
                .clip(CircleShape)
        ) {
            Box(Modifier.weight(1f).fillMaxHeight().background(AppColors.IranGreen))
            Box(Modifier.weight(1f).fillMaxHeight().background(AppColors.IranWhite))
            Box(Modifier.weight(1f).fillMaxHeight().background(AppColors.IranRed))
        }
    }
}

/** "v1.6.0 (123)" from the build config. */
fun settingsVersionLabel(): String = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

/**
 * Identity card at the top of Settings: app icon, the script wordmark with the lion
 * peeking from behind it, the build version, and the current protocol / region.
 */
@Composable
fun SettingsHeroCard(
    protocolName: String,
    regionName: String,
    protocolIcon: ImageVector,
    regionIcon: ImageVector,
    modifier: Modifier = Modifier
) {
    AzadiGlassCard(
        modifier = modifier,
        // Generous top inset leaves room for the lion popping above the letters.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AzadiAppIconTile(size = 58.dp)
            Spacer(Modifier.width(14.dp))
            // The Latin wordmark and version always read left-to-right (iOS does the same).
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(modifier = Modifier.weight(1f)) {
                    AzadiWordmark(
                        fontSize = 32.sp,
                        color = AppColors.PrimaryText,
                        lionStartDelayMs = 800
                    )
                    Text(
                        text = settingsVersionLabel(),
                        color = AppColors.SecondaryText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsPill(icon = protocolIcon, text = protocolName, modifier = Modifier.weight(1f, fill = false))
            SettingsPill(icon = regionIcon, text = regionName, modifier = Modifier.weight(1f, fill = false))
        }
        Spacer(Modifier.height(14.dp))
        SettingsFlagStripe(Modifier.fillMaxWidth())
    }
}

@Composable
fun SettingsPill(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppColors.IranGreenBright,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(5.dp))
        FitText(
            text = text,
            color = AppColors.PrimaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1)
    }
}

// endregion

fun copyText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, R.string.azadi_copied, Toast.LENGTH_SHORT).show()
}
