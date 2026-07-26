package com.psiphon3.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psiphon3.R
import com.psiphon3.psiphonlibrary.PsiphonConstants
import com.psiphon3.ui.theme.AppColors

enum class VpnConnectionStatus {
    WAITING,
    DISCONNECTED,
    CONNECTING,
    WAITING_FOR_NETWORK,
    CONNECTED,
    ERROR
}

@Composable
fun regionDisplayName(regionCode: String): String {
    val region = ALL_REGIONS.find { it.code == regionCode } ?: ALL_REGIONS.first()
    return stringResource(region.nameResId)
}

@Composable
fun IranFlagStripe(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(50))
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(AppColors.IranGreen))
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(AppColors.IranWhite))
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(AppColors.IranRed))
    }
}

@Composable
fun GlassCard(
    elevated: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val fill = if (elevated) AppColors.CardBackgroundElevated else AppColors.CardBackground
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (elevated) 8.dp else 4.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = Color.Black.copy(alpha = 0.45f),
                spotColor = Color.Black.copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .padding(6.dp),
        content = content
    )
}

@Composable
fun ConnectPowerButton(
    status: VpnConnectionStatus,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    
    val targetAccent = when (status) {
        VpnConnectionStatus.CONNECTED -> AppColors.GlowGreen
        VpnConnectionStatus.CONNECTING, VpnConnectionStatus.WAITING_FOR_NETWORK -> AppColors.IranWhite
        VpnConnectionStatus.WAITING -> AppColors.SubtitleText
        VpnConnectionStatus.ERROR -> AppColors.IranRed
        VpnConnectionStatus.DISCONNECTED -> AppColors.IranRed
    }
    val accent by animateColorAsState(targetAccent, label = "accentColor")

    val targetGradientTop = when (status) {
        VpnConnectionStatus.CONNECTED -> Color(0xFF34C759)
        VpnConnectionStatus.CONNECTING, VpnConnectionStatus.WAITING_FOR_NETWORK -> Color(0xFFE5E5EA)
        else -> Color(0xFFE53935)
    }
    val targetGradientBottom = when (status) {
        VpnConnectionStatus.CONNECTED -> Color(0xFF248A3D)
        VpnConnectionStatus.CONNECTING, VpnConnectionStatus.WAITING_FOR_NETWORK -> Color(0xFFAEAEB2)
        else -> Color(0xFFB71C1C)
    }
    
    val gradTop by animateColorAsState(targetGradientTop, label = "gradTop")
    val gradBottom by animateColorAsState(targetGradientBottom, label = "gradBottom")
    val gradient = Brush.linearGradient(colors = listOf(gradTop, gradBottom))

    val actionLabel = when (status) {
        VpnConnectionStatus.CONNECTED -> stringResource(R.string.dashboard_action_disconnect)
        VpnConnectionStatus.CONNECTING, VpnConnectionStatus.WAITING_FOR_NETWORK ->
            stringResource(R.string.dashboard_connecting)
        else -> stringResource(R.string.dashboard_action_connect)
    }

    val isBusy = status == VpnConnectionStatus.CONNECTING ||
            status == VpnConnectionStatus.WAITING_FOR_NETWORK

    val infiniteTransition = rememberInfiniteTransition(label = "powerButton")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val spin by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1050, easing = LinearEasing)
        ),
        label = "spin"
    )

    // Shimmer effect offset
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && isEnabled) 0.92f else 1f,
        animationSpec = spring(stiffness = 520f, dampingRatio = 0.72f),
        label = "pressScale"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (isBusy) 0.85f else 1f,
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.6f),
        label = "iconScale"
    )

    var previousStatus by remember { mutableStateOf(status) }
    LaunchedEffect(status) {
        if (previousStatus != status) {
            if (status == VpnConnectionStatus.CONNECTED || status == VpnConnectionStatus.DISCONNECTED) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            previousStatus = status
        }
    }

    // Decorative rings only — no click/ripple on the outer area.
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(180.dp)
            .alpha(if (isEnabled) 1f else 0.42f)
    ) {
        if (status == VpnConnectionStatus.CONNECTED) {
            // Connected pulsing halos
            listOf(164, 150, 136).forEachIndexed { index, size ->
                val baseAlpha = 0.14f - index * 0.03f
                val animatedAlpha = baseAlpha + (pulse * 0.06f)
                Box(
                    modifier = Modifier
                        .size((size + pulse * 10).dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    AppColors.GlowGreen.copy(alpha = animatedAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .size((160 + pulse * 12).dp)
                    .clip(CircleShape)
                    .border(
                        1.2.dp,
                        AppColors.GlowGreen.copy(alpha = 0.18f + pulse * 0.22f),
                        CircleShape
                    )
            )
        } else if (status == VpnConnectionStatus.DISCONNECTED) {
            // Disconnected slow breathing
            listOf(164, 150, 136).forEachIndexed { index, size ->
                val baseAlpha = 0.10f - index * 0.025f
                val animatedAlpha = baseAlpha + (pulse * 0.04f)
                Box(
                    modifier = Modifier
                        .size(size.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    AppColors.IranRed.copy(alpha = animatedAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .size((160 + pulse * 8).dp)
                    .clip(CircleShape)
                    .border(
                        1.2.dp,
                        AppColors.IranRed.copy(alpha = 0.12f + pulse * 0.14f),
                        CircleShape
                    )
            )
        } else if (isBusy) {
            // Pulsing halo for connecting state
            listOf(174, 160, 146).forEachIndexed { index, size ->
                Box(
                    modifier = Modifier
                        .size(size.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.15f - index * 0.04f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .size((166 + pulse * 12).dp)
                    .clip(CircleShape)
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.12f + pulse * 0.18f),
                        CircleShape
                    )
            )
        }

        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(
                                alpha = when (status) {
                                    VpnConnectionStatus.CONNECTED -> 0.35f
                                    VpnConnectionStatus.DISCONNECTED -> 0.28f
                                    else -> 0.12f
                                }
                            ),
                            Color.Transparent
                        )
                    )
                )
        )

        if (isBusy) {
            Canvas(modifier = Modifier.size(130.dp)) {
                val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = AppColors.GlowGreen.copy(alpha = 0.85f),
                    startAngle = spin,
                    sweepAngle = 95f,
                    useCenter = false,
                    style = stroke,
                    topLeft = Offset(6.dp.toPx(), 6.dp.toPx()),
                    size = Size(size.width - 12.dp.toPx(), size.height - 12.dp.toPx())
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .scale(pressScale)
                .size(116.dp)
                .shadow(16.dp, CircleShape, ambientColor = accent.copy(alpha = 0.45f), spotColor = accent.copy(alpha = 0.55f))
                .clip(CircleShape)
                .background(gradient)
                .border(
                    1.2.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = isEnabled,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                )
        ) {
            // Surface Shimmer (diagonal sweep)
            if (status == VpnConnectionStatus.CONNECTED) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0f),
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0f)
                        ),
                        start = Offset(shimmerTranslate, shimmerTranslate),
                        end = Offset(shimmerTranslate + 100f, shimmerTranslate + 100f)
                    )
                    drawRect(brush = brush)
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.scale(iconScale)
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = stringResource(R.string.dashboard_toggle_vpn),
                    tint = Color.White,
                    modifier = Modifier.size(if (isBusy) 32.dp else 36.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = actionLabel,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun TrafficStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconBackground: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconBackground.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconBackground, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, color = AppColors.SubtitleText, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionPickerSheet(
    selectedRegionCode: String,
    onRegionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    regionCodes: List<String>? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val regions = remember(regionCodes) {
        val codes = regionCodes ?: com.psiphon3.azadi.PsiphonRegionList.all
        codes.mapNotNull { code -> ALL_REGIONS.find { it.code == code } }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E)
    ) {
        Text(
            text = stringResource(R.string.dashboard_region),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            items(regions) { region ->
                val isSelected = region.code == selectedRegionCode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onRegionSelected(region.code)
                            onDismiss()
                        }
                        .background(
                            if (isSelected) AppColors.IranGreen.copy(alpha = 0.15f) else Color.Transparent
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = region.flagResId),
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = stringResource(id = region.nameResId),
                        color = Color.White,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LocationCard(
    selectedRegionCode: String,
    connectedLocationLine: String?,
    regionHintLine: String?,
    publicIp: String,
    onRegionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentRegion = ALL_REGIONS.find { it.code == selectedRegionCode } ?: ALL_REGIONS.first()

    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                tint = AppColors.GlowGreen,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dashboard_region),
                    color = AppColors.SubtitleText,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onRegionClick)
                        .padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = currentRegion.flagResId),
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(id = currentRegion.nameResId),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.UnfoldMore,
                        contentDescription = null,
                        tint = AppColors.GlowGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }

                val subtitle = connectedLocationLine ?: regionHintLine
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        color = if (connectedLocationLine != null) AppColors.GlowGreen else AppColors.SubtitleText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider(
                    color = AppColors.CardStroke,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_public_ip),
                        color = AppColors.SubtitleText,
                        fontSize = 12.sp
                    )
                    Text(
                        text = publicIp,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun AzadiFloatingBottomNav(
    selectedTab: Int,
    onVpnClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .height(49.dp)
                .shadow(8.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(0.45f))
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF1C1C1E).copy(alpha = 0.94f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 3.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                selected = selectedTab == 0,
                icon = Icons.Default.Security,
                label = stringResource(R.string.dashboard_tab_vpn),
                selectedColor = AppColors.NavBlue,
                onClick = onVpnClick
            )
            BottomNavItem(
                selected = selectedTab == 1,
                icon = Icons.Default.Settings,
                label = stringResource(R.string.azadi_tab_settings),
                selectedColor = AppColors.NavBlue,
                onClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .defaultMinSize(minWidth = 76.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(if (selected) selectedColor.copy(alpha = 0.16f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) selectedColor else Color.White.copy(0.65f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(1.dp))
        Text(
            label,
            color = if (selected) selectedColor else Color.White.copy(0.65f),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

data class RegionItem(
    val code: String,
    val nameResId: Int,
    val flagResId: Int
)

val ALL_REGIONS = listOf(
    RegionItem(PsiphonConstants.REGION_CODE_ANY, R.string.region_name_any, R.drawable.flag_any),
    RegionItem("AE", R.string.region_name_ae, R.drawable.flag_ae),
    RegionItem("AR", R.string.region_name_ar, R.drawable.flag_ar),
    RegionItem("AT", R.string.region_name_at, R.drawable.flag_at),
    RegionItem("AU", R.string.region_name_au, R.drawable.flag_au),
    RegionItem("BE", R.string.region_name_be, R.drawable.flag_be),
    RegionItem("BG", R.string.region_name_bg, R.drawable.flag_bg),
    RegionItem("BR", R.string.region_name_br, R.drawable.flag_br),
    RegionItem("CA", R.string.region_name_ca, R.drawable.flag_ca),
    RegionItem("CH", R.string.region_name_ch, R.drawable.flag_ch),
    RegionItem("CL", R.string.region_name_cl, R.drawable.flag_cl),
    RegionItem("CO", R.string.region_name_co, R.drawable.flag_co),
    RegionItem("CZ", R.string.region_name_cz, R.drawable.flag_cz),
    RegionItem("DE", R.string.region_name_de, R.drawable.flag_de),
    RegionItem("DK", R.string.region_name_dk, R.drawable.flag_dk),
    RegionItem("EE", R.string.region_name_ee, R.drawable.flag_ee),
    RegionItem("ES", R.string.region_name_es, R.drawable.flag_es),
    RegionItem("FI", R.string.region_name_fi, R.drawable.flag_fi),
    RegionItem("FR", R.string.region_name_fr, R.drawable.flag_fr),
    RegionItem("GB", R.string.region_name_gb, R.drawable.flag_gb),
    RegionItem("GR", R.string.region_name_gr, R.drawable.flag_gr),
    RegionItem("HK", R.string.region_name_hk, R.drawable.flag_hk),
    RegionItem("HR", R.string.region_name_hr, R.drawable.flag_hr),
    RegionItem("HU", R.string.region_name_hu, R.drawable.flag_hu),
    RegionItem("ID", R.string.region_name_id, R.drawable.flag_id),
    RegionItem("IE", R.string.region_name_ie, R.drawable.flag_ie),
    RegionItem("IN", R.string.region_name_in, R.drawable.flag_in),
    RegionItem("IS", R.string.region_name_is, R.drawable.flag_is),
    RegionItem("IT", R.string.region_name_it, R.drawable.flag_it),
    RegionItem("JP", R.string.region_name_jp, R.drawable.flag_jp),
    RegionItem("KE", R.string.region_name_ke, R.drawable.flag_ke),
    RegionItem("KR", R.string.region_name_kr, R.drawable.flag_kr),
    RegionItem("LT", R.string.region_name_lt, R.drawable.flag_lt),
    RegionItem("LV", R.string.region_name_lv, R.drawable.flag_lv),
    RegionItem("MX", R.string.region_name_mx, R.drawable.flag_mx),
    RegionItem("MY", R.string.region_name_my, R.drawable.flag_my),
    RegionItem("NL", R.string.region_name_nl, R.drawable.flag_nl),
    RegionItem("NO", R.string.region_name_no, R.drawable.flag_no),
    RegionItem("NZ", R.string.region_name_nz, R.drawable.flag_nz),
    RegionItem("PL", R.string.region_name_pl, R.drawable.flag_pl),
    RegionItem("PT", R.string.region_name_pt, R.drawable.flag_pt),
    RegionItem("RO", R.string.region_name_ro, R.drawable.flag_ro),
    RegionItem("RS", R.string.region_name_rs, R.drawable.flag_rs),
    RegionItem("SE", R.string.region_name_se, R.drawable.flag_se),
    RegionItem("SG", R.string.region_name_sg, R.drawable.flag_sg),
    RegionItem("SK", R.string.region_name_sk, R.drawable.flag_sk),
    RegionItem("TW", R.string.region_name_tw, R.drawable.flag_tw),
    RegionItem("UA", R.string.region_name_ua, R.drawable.flag_ua),
    RegionItem("US", R.string.region_name_us, R.drawable.flag_us),
    RegionItem("ZA", R.string.region_name_za, R.drawable.flag_za)
)
