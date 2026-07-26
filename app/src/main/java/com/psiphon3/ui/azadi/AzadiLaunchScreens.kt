package com.psiphon3.ui.azadi

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psiphon3.R
import com.psiphon3.azadi.AzadiEventLogger
import com.psiphon3.ui.IranFlagStripe
import com.psiphon3.ui.theme.AppColors
import kotlinx.coroutines.delay

enum class LaunchPhase {
    LANGUAGE,
    SPLASH,
    ONBOARDING,
    DONE
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "LogoScale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "ContentAlpha"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(1800)
        onFinished()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .scale(scale)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(alpha)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                IranFlagStripe(modifier = Modifier.width(120.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.azadi_splash_tagline),
                    color = AppColors.SubtitleText,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun LanguageSelectionScreen(
    onSelectEnglish: () -> Unit,
    onSelectPersian: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(24.dp)
            .testTag("languageSelectionScreen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_app_logo),
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.app_name),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.azadi_choose_language),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.azadi_choose_language_fa),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        LanguageOptionCard(
            title = stringResource(R.string.azadi_lang_english),
            subtitle = stringResource(R.string.azadi_lang_english_subtitle),
            onClick = onSelectEnglish,
            modifier = Modifier.testTag("language_picker_english")
        )
        Spacer(modifier = Modifier.height(14.dp))
        LanguageOptionCard(
            title = stringResource(R.string.azadi_lang_persian),
            subtitle = stringResource(R.string.azadi_lang_persian_subtitle),
            onClick = onSelectPersian,
            modifier = Modifier.testTag("language_picker_persian")
        )
    }
}

@Composable
private fun LanguageOptionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.CardBackgroundElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = AppColors.SubtitleText, fontSize = 13.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.SubtitleText)
    }
}

private data class OnboardingPage(
    val icon: ImageVector?,
    val usesLogo: Boolean = false,
    val titleRes: Int,
    val bodyRes: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit, onSkip: () -> Unit = onComplete) {
    var page by remember { mutableIntStateOf(0) }
    val pages = listOf(
        OnboardingPage(null, true, R.string.azadi_onboarding_welcome_title, R.string.azadi_onboarding_welcome_body),
        OnboardingPage(Icons.Default.Security, false, R.string.azadi_onboarding_privacy_title, R.string.azadi_onboarding_privacy_body),
        OnboardingPage(Icons.Default.Public, false, R.string.azadi_onboarding_transport_title, R.string.azadi_onboarding_transport_body),
        OnboardingPage(Icons.Default.AccountTree, false, R.string.azadi_onboarding_fallback_title, R.string.azadi_onboarding_fallback_body),
        OnboardingPage(Icons.Default.Favorite, false, R.string.azadi_onboarding_support_title, R.string.azadi_onboarding_support_body)
    )
    val current = pages[page]

    BackHandler {
        if (page > 0) page-- else onSkip()
    }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.azadi_onboarding_nav_title), color = Color.White) },
                navigationIcon = {
                    if (page > 0) {
                        TextButton(onClick = {
                            AzadiEventLogger.logSync("ONBOARDING_COMPLETED", "skipped=true")
                            onSkip()
                        }) {
                            Text(stringResource(R.string.azadi_onboarding_skip), color = AppColors.SubtitleText)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            if (current.usesLogo) {
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                )
            } else if (current.icon != null) {
                Icon(
                    current.icon,
                    contentDescription = null,
                    tint = AppColors.IranGreenBright,
                    modifier = Modifier.size(56.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(current.titleRes),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(current.bodyRes),
                color = AppColors.SubtitleText,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            LinearProgressIndicator(
                progress = { (page + 1f) / pages.size },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (page < pages.lastIndex) page++ else {
                        AzadiEventLogger.logSync("ONBOARDING_COMPLETED", "pages=${pages.size}")
                        onComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_continue_button")
            ) {
                Text(
                    if (page < pages.lastIndex) stringResource(R.string.azadi_onboarding_continue)
                    else stringResource(R.string.azadi_get_started)
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDisclaimerSheet(
    onAccept: () -> Unit,
    onCancel: () -> Unit
) {
    LaunchedEffect(Unit) {
        AzadiEventLogger.logSync("DISCLAIMER_PRESENTED", "source=first_connect")
    }

    ModalBottomSheet(
        onDismissRequest = onCancel,
        containerColor = Color(0xFF1A1A1A),
        modifier = Modifier.testTag("connectionDisclaimerSheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.azadi_disclaimer_title),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.azadi_disclaimer_intro),
                color = AppColors.SubtitleText,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(18.dp))
            DisclaimerSection(
                title = stringResource(R.string.azadi_disclaimer_responsible_title),
                body = stringResource(R.string.azadi_disclaimer_responsible_body)
            )
            DisclaimerSection(
                title = stringResource(R.string.azadi_disclaimer_no_guarantee_title),
                body = stringResource(R.string.azadi_disclaimer_no_guarantee_body)
            )
            DisclaimerSection(
                title = stringResource(R.string.azadi_disclaimer_privacy_diag_title),
                body = stringResource(R.string.azadi_disclaimer_privacy_diag_body)
            )
            DisclaimerSection(
                title = stringResource(R.string.azadi_disclaimer_no_illegal_title),
                body = stringResource(R.string.azadi_disclaimer_no_illegal_body)
            )
            DisclaimerSection(
                title = stringResource(R.string.azadi_disclaimer_third_party_title),
                body = stringResource(R.string.azadi_disclaimer_third_party_body)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("disclaimerCancelButton")
                ) {
                    Text(stringResource(R.string.azadi_cancel))
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("disclaimerAcceptButton")
                ) {
                    Text(stringResource(R.string.azadi_i_agree))
                }
            }
        }
    }
}

@Composable
private fun DisclaimerSection(title: String, body: String) {
    Spacer(modifier = Modifier.height(14.dp))
    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    Spacer(modifier = Modifier.height(6.dp))
    Text(body, color = AppColors.SubtitleText, fontSize = 14.sp)
}
