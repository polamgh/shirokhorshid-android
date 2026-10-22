package com.psiphon3.ui.azadi

import com.psiphon3.ui.redesign.FitText

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psiphon3.BuildConfig
import com.psiphon3.R
import com.psiphon3.azadi.AzadiEventLogger
import com.psiphon3.azadi.LegalNoticesCatalog
import com.psiphon3.ui.redesign.AzadiWordmark
import com.psiphon3.ui.theme.AppColors
import java.util.Calendar

private object AboutLinks {
    const val COMPANY_WEBSITE = "https://debugsy.com"
    const val X_PROFILE = "https://x.com/alighanavatidev"
    const val CONTACT_EMAIL = "alighanavati@debugsy.com"
    const val PSIPHON_WEBSITE = "https://psiphon.ca"
    const val PSIPHON_GITHUB = "https://github.com/psiphon-inc"
}

/**
 * About AzadiTunnel, ported from the iOS AboutView: haloed app icon, script wordmark with
 * the peeking lion, info tiles, notices, contact grid, project links and legal links.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNavigatePrivacy: () -> Unit,
    onNavigateLegal: () -> Unit,
    onNavigateSupport: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val year = remember { Calendar.getInstance().get(Calendar.YEAR) }
    var appeared by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AzadiEventLogger.logSync(
            "ABOUT_PAGE_OPENED",
            "version=${BuildConfig.VERSION_NAME} build=${BuildConfig.VERSION_CODE}"
        )
        appeared = true
    }

    AzadiSettingsSurface {
        Column(modifier = Modifier.fillMaxSize()) {
            AboutTopBar(title = stringResource(R.string.azadi_about_app), onBack = onBack)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .testTag("aboutAzadiTunnelScreen")
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    AboutAppear(index = 0, appeared = appeared) { AboutHero() }
                    AboutAppear(index = 1, appeared = appeared) { AboutInfoTiles() }
                    AboutAppear(index = 2, appeared = appeared) { AboutNoticeCard() }
                    AboutAppear(index = 3, appeared = appeared) {
                        AboutContactGrid(
                            onRate = { openStoreListing(context) },
                            onCompany = { openUrl(context, AboutLinks.COMPANY_WEBSITE) },
                            onX = { openUrl(context, AboutLinks.X_PROFILE) },
                            onContact = { openEmail(context, AboutLinks.CONTACT_EMAIL) }
                        )
                    }
                    AboutAppear(index = 4, appeared = appeared) {
                        AzadiGlassCard {
                            AboutRow(
                                icon = Icons.Filled.Explore,
                                tint = SettingsTints.Blue,
                                title = stringResource(R.string.settings2_about_psiphon_website),
                                external = true,
                                onClick = { openUrl(context, AboutLinks.PSIPHON_WEBSITE) }
                            )
                            AboutRowDivider()
                            AboutRow(
                                icon = Icons.Filled.Link,
                                tint = SettingsTints.Gray,
                                title = stringResource(R.string.azadi_psiphon_github),
                                external = true,
                                onClick = { openUrl(context, AboutLinks.PSIPHON_GITHUB) }
                            )
                        }
                    }
                    AboutAppear(index = 5, appeared = appeared) {
                        AzadiGlassCard {
                            AboutRow(
                                icon = Icons.Filled.PanTool,
                                tint = SettingsTints.Purple,
                                title = stringResource(R.string.azadi_privacy_notice),
                                external = false,
                                onClick = onNavigatePrivacy
                            )
                            AboutRowDivider()
                            AboutRow(
                                icon = Icons.Filled.Description,
                                tint = SettingsTints.Orange,
                                title = stringResource(R.string.azadi_legal_opensource),
                                external = false,
                                onClick = onNavigateLegal
                            )
                            if (onNavigateSupport != null) {
                                AboutRowDivider()
                                AboutRow(
                                    icon = Icons.Filled.Favorite,
                                    tint = AppColors.IranRed,
                                    title = stringResource(R.string.azadi_support_app),
                                    external = false,
                                    onClick = onNavigateSupport
                                )
                            }
                        }
                    }
                    AboutAppear(index = 6, appeared = appeared) {
                        Text(
                            text = stringResource(R.string.settings2_about_copyright, year),
                            color = AppColors.SecondaryText,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutTopBar(title: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings2_back),
                tint = AppColors.PrimaryText
            )
        }
        FitText(
            text = title,
            color = AppColors.PrimaryText,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp)
                .semantics { heading() }
        )
    }
}

// region Hero

@Composable
private fun AboutHero() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(150.dp),
            contentAlignment = Alignment.Center
        ) {
            AboutHalo(Modifier.fillMaxSize())
            AzadiAppIconTile(size = 96.dp)
        }
        Spacer(Modifier.height(20.dp))
        AzadiWordmark(
            modifier = Modifier.padding(horizontal = 6.dp),
            fontSize = 44.sp,
            color = AppColors.PrimaryText,
            lionStartDelayMs = 1000,
            lionExtraDrop = 6.dp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = settingsVersionLabel(),
            color = AppColors.SecondaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.azadi_about_mission),
            color = AppColors.SecondaryText,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(16.dp))
        SettingsFlagStripe(Modifier.width(120.dp))
    }
}

/** Flag-colored ring that slowly turns around the app icon, over a soft green glow. */
@Composable
private fun AboutHalo(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aboutHalo")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 10_500, easing = LinearEasing)),
        label = "aboutHaloAngle"
    )
    val ringColors = remember {
        listOf(
            AppColors.IranGreenBright,
            AppColors.IranWhite.copy(alpha = 0.9f),
            AppColors.IranRed,
            AppColors.IranGreenBright.copy(alpha = 0.1f),
            AppColors.IranGreenBright
        )
    }
    Canvas(modifier = modifier) {
        val outer = size.minDimension / 2f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AppColors.IranGreenBright.copy(alpha = 0.22f), Color.Transparent),
                center = center,
                radius = outer
            ),
            radius = outer,
            center = center
        )
        rotate(degrees = angle, pivot = center) {
            drawCircle(
                brush = Brush.sweepGradient(colors = ringColors, center = center),
                radius = outer - 14.dp.toPx(),
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }
    }
}

// endregion

// region Info + notices

@Composable
private fun AboutInfoTiles() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AboutInfoTile(
            icon = Icons.Filled.Person,
            tint = SettingsTints.Blue,
            label = stringResource(R.string.settings2_about_company_label),
            value = stringResource(R.string.settings2_about_company_value),
            modifier = Modifier.weight(1f)
        )
        AboutInfoTile(
            icon = Icons.Filled.AppShortcut,
            tint = AppColors.IranGreen,
            label = stringResource(R.string.settings2_about_app_version_label),
            value = BuildConfig.VERSION_NAME,
            modifier = Modifier.weight(1f)
        )
        AboutInfoTile(
            icon = Icons.Filled.Memory,
            tint = SettingsTints.Orange,
            label = stringResource(R.string.azadi_core_version_label),
            value = stringResource(R.string.azadi_core_version_value),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AboutInfoTile(
    icon: ImageVector,
    tint: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    AzadiGlassCard(
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 112.dp)
            .semantics(mergeDescendants = true) { },
        contentPadding = PaddingValues(12.dp)
    ) {
        SettingsIconTile(icon = icon, tint = tint)
        Spacer(Modifier.height(8.dp))
        FitText(
            text = label,
            color = AppColors.SecondaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1)
        Spacer(Modifier.height(4.dp))
        FitText(
            text = value,
            color = AppColors.PrimaryText,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2)
    }
}

@Composable
private fun AboutNoticeCard() {
    AzadiGlassCard(contentPadding = PaddingValues(16.dp)) {
        AboutNoticeRow(Icons.Filled.VerifiedUser, AppColors.IranGreen, stringResource(R.string.azadi_about_responsible_use))
        AboutNoticeDivider()
        AboutNoticeRow(Icons.Filled.Code, SettingsTints.Indigo, stringResource(R.string.azadi_about_opensource_ack))
        AboutNoticeDivider()
        AboutNoticeRow(Icons.Filled.Info, SettingsTints.Gray, stringResource(R.string.azadi_psiphon_affiliation_disclaimer))
        AboutNoticeDivider()
        AboutNoticeRow(Icons.Filled.Gavel, SettingsTints.Teal, stringResource(R.string.azadi_content_policy))
    }
}

@Composable
private fun AboutNoticeRow(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        SettingsIconTile(icon = icon, tint = tint)
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            color = AppColors.SecondaryText,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AboutNoticeDivider() {
    HorizontalDivider(
        color = AppColors.CardStroke,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

// endregion

// region Links

@Composable
private fun AboutContactGrid(
    onRate: () -> Unit,
    onCompany: () -> Unit,
    onX: () -> Unit,
    onContact: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AboutLinkTile(
                icon = Icons.Filled.Star,
                tint = SettingsTints.Yellow,
                title = stringResource(R.string.settings2_about_rate_us),
                onClick = onRate,
                modifier = Modifier.weight(1f)
            )
            AboutLinkTile(
                icon = Icons.Filled.Business,
                tint = SettingsTints.Blue,
                title = stringResource(R.string.settings2_about_company_value),
                onClick = onCompany,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AboutLinkTile(
                icon = Icons.Filled.AlternateEmail,
                tint = SettingsTints.Graphite,
                title = stringResource(R.string.settings2_about_x),
                onClick = onX,
                modifier = Modifier.weight(1f)
            )
            AboutLinkTile(
                icon = Icons.Filled.Email,
                tint = AppColors.IranGreen,
                title = stringResource(R.string.settings2_about_contact_us),
                onClick = onContact,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AboutLinkTile(
    icon: ImageVector,
    tint: Color,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 700f),
        label = "aboutTilePress"
    )
    Row(
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 58.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (pressed) 0.85f else 1f
            }
            .clip(SettingsCardShape)
            .background(AppColors.Glass)
            .border(1.dp, AppColors.GlassStroke, SettingsCardShape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconTile(icon = icon, tint = tint)
        Spacer(Modifier.width(10.dp))
        FitText(
            text = title,
            color = AppColors.PrimaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(4.dp))
        ExternalLinkGlyph()
    }
}

@Composable
private fun ExternalLinkGlyph() {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(
        imageVector = if (rtl) Icons.Filled.NorthWest else Icons.Filled.NorthEast,
        contentDescription = null,
        tint = AppColors.SecondaryText,
        modifier = Modifier.size(14.dp)
    )
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    external: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconTile(icon = icon, tint = tint)
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            color = AppColors.PrimaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        if (external) {
            ExternalLinkGlyph()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppColors.SecondaryText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AboutRowDivider() {
    HorizontalDivider(
        color = AppColors.CardStroke,
        modifier = Modifier.padding(start = 58.dp)
    )
}

// endregion

/** Staggered fade + rise on first appearance (iOS AboutAppear). */
@Composable
private fun AboutAppear(index: Int, appeared: Boolean, content: @Composable () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 450, delayMillis = index * 60, easing = FastOutSlowInEasing),
        label = "aboutAppear$index"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 16.dp.toPx()
            }
    ) {
        content()
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
    }
}

private fun openEmail(context: Context, address: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))
    } catch (e: ActivityNotFoundException) {
    }
}

private fun openStoreListing(context: Context) {
    val pkg = context.packageName
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
    } catch (e: ActivityNotFoundException) {
        openUrl(context, "https://play.google.com/store/apps/details?id=$pkg")
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    LaunchedEffect(Unit) {
        AzadiEventLogger.logSync("PRIVACY_PAGE_OPENED", "source=settings")
    }

    AzadiSubScreenScaffold(
        title = stringResource(R.string.azadi_privacy_notice),
        onBack = onBack
    ) {
        AzadiSettingsGroup {
            PrivacyParagraph(stringResource(R.string.azadi_privacy_body))
            PrivacyParagraph(stringResource(R.string.azadi_privacy_no_secrets))
            PrivacyParagraph(stringResource(R.string.azadi_privacy_review_export))
            PrivacyParagraph(stringResource(R.string.azadi_privacy_play_billing))
        }
    }
}

@Composable
private fun PrivacyParagraph(text: String) {
    Text(text, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
    Spacer(Modifier.height(14.dp))
}

@Composable
fun LegalScreen(onBack: () -> Unit, onNavigateFullLicense: () -> Unit) {
    val context = LocalContext.current
    val preview = remember { LegalNoticesCatalog.appLicensePreview(context) }
    val licenseUnavailable = preview.isBlank()

    LaunchedEffect(Unit) {
        LegalNoticesCatalog.logMissingComponentsOnOpen(context)
        AzadiEventLogger.logSync("LEGAL_PAGE_OPENED", "screen=legal_open_source")
    }

    AzadiSubScreenScaffold(
        title = stringResource(R.string.azadi_legal_opensource),
        onBack = onBack
    ) {
        AzadiSectionHeader(stringResource(R.string.azadi_legal_oss_section))
        LegalNoticesCatalog.components.forEach { component ->
            AzadiSettingsGroup {
                Text(component.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(component.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.azadi_legal_license_label, component.license),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(4.dp))
                AzadiActionLink(
                    text = component.sourceUrl,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(component.sourceUrl)))
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        AzadiSectionHeader(stringResource(R.string.azadi_legal_app_license_section))
        AzadiSettingsGroup {
            Text("AzadiTunnel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (licenseUnavailable) stringResource(R.string.azadi_legal_license_unavailable) else preview,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(12.dp))
        AzadiSectionHeader(stringResource(R.string.azadi_legal_gpl_warning_section))
        AzadiSettingsGroup {
            AzadiFooterNote(stringResource(R.string.azadi_legal_gpl_warning_body))
        }

        Spacer(Modifier.height(12.dp))
        AzadiSettingsGroup {
            AzadiNavRow(
                title = stringResource(R.string.azadi_view_full_license),
                onClick = onNavigateFullLicense,
                testTag = "viewFullLicenseNoticesLink"
            )
        }
    }
}

@Composable
fun FullLicenseNoticesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val fullText = remember { LegalNoticesCatalog.fullLicenseNoticesText(context) }

    LaunchedEffect(Unit) {
        AzadiEventLogger.logSync("LICENSE_NOTICES_OPENED", "source=legal_page")
    }

    AzadiSubScreenScaffold(
        title = stringResource(R.string.azadi_view_full_license),
        onBack = onBack
    ) {
        // The scaffold already scrolls vertically; a nested verticalScroll would be measured
        // with infinite height and throw.
        AzadiSettingsGroup {
            Text(
                text = fullText,
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp
            )
        }
    }
}
