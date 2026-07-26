package com.psiphon3.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psiphon3.R
import com.psiphon3.psiphonlibrary.MoreOptionsPreferenceActivity
import com.psiphon3.psiphonlibrary.ProxyOptionsPreferenceActivity
import com.psiphon3.psiphonlibrary.Utils
import com.psiphon3.psiphonlibrary.VpnOptionsPreferenceActivity
import com.psiphon3.ui.theme.AppColors

@Composable
fun SettingsScreen(
    onOpenLogs: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_tab_name),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        SettingsSection(title = stringResource(R.string.dashboard_connection)) {
            SettingsNavItem(
                label = stringResource(R.string.label_vpn_settings),
                enabled = Utils.supportsVpnExclusions(),
                subtitle = if (Utils.supportsVpnExclusions()) null
                else stringResource(R.string.vpn_exclusions_preference_not_available_summary),
                onClick = {
                    context.startActivity(Intent(context, VpnOptionsPreferenceActivity::class.java))
                }
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            SettingsNavItem(
                label = stringResource(R.string.label_proxy_settings),
                onClick = {
                    context.startActivity(Intent(context, ProxyOptionsPreferenceActivity::class.java))
                }
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            SettingsNavItem(
                label = stringResource(R.string.moreOptions),
                onClick = {
                    context.startActivity(Intent(context, MoreOptionsPreferenceActivity::class.java))
                }
            )
        }

        SettingsSection(title = stringResource(R.string.logs_tab_name)) {
            SettingsNavItem(
                label = stringResource(R.string.logs_tab_name),
                icon = Icons.AutoMirrored.Filled.List,
                onClick = onOpenLogs
            )
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title.uppercase(),
            color = AppColors.SubtitleText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        GlassCard(content = content)
    }
}

@Composable
fun SettingsNavItem(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) Modifier else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = AppColors.IranGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column {
                        Text(label, color = if (enabled) Color.White else AppColors.SubtitleText, fontSize = 16.sp)
                        if (subtitle != null) {
                            Text(subtitle, color = AppColors.SubtitleText, fontSize = 12.sp)
                        }
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.SubtitleText)
            }
        }
    }
}

@Composable
fun SettingsSwitchItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}
