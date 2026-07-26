package com.psiphon3.ui.azadi

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psiphon3.BuildConfig
import com.psiphon3.R
import com.psiphon3.azadi.AzadiEventLogger
import com.psiphon3.azadi.LegalNoticesCatalog
import java.util.Calendar

@Composable
fun AboutScreen(onBack: () -> Unit, onNavigatePrivacy: () -> Unit, onNavigateLegal: () -> Unit) {
    val context = LocalContext.current
    val year = Calendar.getInstance().get(Calendar.YEAR)

    LaunchedEffect(Unit) {
        AzadiEventLogger.logSync(
            "ABOUT_PAGE_OPENED",
            "version=${BuildConfig.VERSION_NAME} build=${BuildConfig.VERSION_CODE}"
        )
    }

    AzadiSubScreenScaffold(
        title = stringResource(R.string.azadi_about_app),
        onBack = onBack
    ) {
        AzadiSettingsGroup {
            Text(
                text = stringResource(R.string.azadi_about_mission),
                color = Color.White,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.azadi_about_responsible_use),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.azadi_about_opensource_ack),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        AzadiSettingsGroup {
            AzadiValueRow(stringResource(R.string.azadi_version_label), BuildConfig.VERSION_NAME)
            AzadiDivider()
            AzadiValueRow(stringResource(R.string.azadi_build_label), BuildConfig.VERSION_CODE.toString())
            AzadiDivider()
            AzadiValueRow(stringResource(R.string.azadi_core_version_label), stringResource(R.string.azadi_core_version_value))
            AzadiDivider()
            AzadiValueRow(stringResource(R.string.azadi_developer_label), stringResource(R.string.azadi_developer_name))
        }
        Spacer(Modifier.height(12.dp))
        AzadiSettingsGroup {
            AzadiFooterNote(stringResource(R.string.azadi_psiphon_affiliation_disclaimer))
            AzadiDivider()
            AzadiFooterNote(stringResource(R.string.azadi_content_policy))
        }
        Spacer(Modifier.height(12.dp))
        AzadiSettingsGroup {
            AzadiActionLink(
                text = stringResource(R.string.azadi_website),
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://psiphon.ca"))) }
            )
            AzadiDivider()
            AzadiActionLink(
                text = stringResource(R.string.azadi_psiphon_github),
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/psiphon-inc"))) }
            )
            AzadiDivider()
            AzadiActionLink(
                text = stringResource(R.string.azadi_twitter),
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://x.com/alighanavatidev"))) }
            )
            AzadiDivider()
            AzadiActionLink(
                text = stringResource(R.string.azadi_contact),
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("mailto:alighanavatidev@gmail.com"))) }
            )
            AzadiDivider()
            AzadiNavRow(title = stringResource(R.string.azadi_privacy_notice), onClick = onNavigatePrivacy)
            AzadiDivider()
            AzadiNavRow(title = stringResource(R.string.azadi_legal_opensource), onClick = onNavigateLegal)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.azadi_about_copyright, year),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
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
}
