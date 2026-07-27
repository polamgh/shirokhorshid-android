package com.psiphon3.ui.azadi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.psiphon3.R
import com.psiphon3.ui.GlassCard
import com.psiphon3.ui.theme.AppColors

val AzadiWarningOrange = Color(0xFFFF9500)
val AzadiActionBlue = Color(0xFF0A84FF)
val AzadiDestructiveRed = Color(0xFFFF453A)

@Composable
fun AzadiSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        color = AppColors.SubtitleText,
        fontSize = 13.sp,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp, top = 12.dp)
    )
}

@Composable
fun AzadiSettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    GlassCard(content = content)
}

@Composable
fun AzadiDivider() {
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.08f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
fun AzadiToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    warning: String? = null,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    Column(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppColors.ConnectedGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.Gray.copy(alpha = 0.5f)
                )
            )
        }
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = AppColors.SubtitleText, fontSize = 13.sp, lineHeight = 18.sp)
        }
        if (!warning.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(warning, color = AzadiWarningOrange, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
fun AzadiPickerRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, fontSize = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = AppColors.SubtitleText, fontSize = 16.sp)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = AppColors.SubtitleText, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun AzadiValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 16.sp)
        Text(value, color = AppColors.SubtitleText, fontSize = 16.sp)
    }
}

@Composable
fun AzadiNavRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    Row(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = AppColors.SubtitleText, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.SubtitleText)
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
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = AppColors.SubtitleText, fontSize = 14.sp)
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
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text, color = color, fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
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
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = AppColors.SubtitleText, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (selected) AppColors.NavBlue else AppColors.SubtitleText.copy(alpha = 0.5f),
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
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = AppColors.SubtitleText
            )
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

@Composable
fun AzadiFooterNote(text: String) {
    Text(
        text = text,
        color = AppColors.SubtitleText,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
fun AzadiWarningBlock(text: String) {
    Text(
        text = text,
        color = AzadiWarningOrange,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

fun copyText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, R.string.azadi_copied, Toast.LENGTH_SHORT).show()
}
