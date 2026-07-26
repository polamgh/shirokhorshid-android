package com.psiphon3.ui

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import com.psiphon3.azadi.AppLocaleHelper
import com.psiphon3.psiphonlibrary.LocaleManager

@Composable
fun ProvideAppLocale(
    languageCode: String,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val code = languageCode.ifEmpty { "system" }

    val activity = baseContext as? Activity
    SideEffect {
        activity?.let { AppLocaleHelper.applyToActivity(it, code) }
    }

    val configuration = remember(code) {
        LocaleManager.getInstance(baseContext).wrapWithLanguage(baseContext, code).resources.configuration
    }
    val layoutDirection = if (configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    CompositionLocalProvider(
        LocalConfiguration provides Configuration(configuration),
        LocalLayoutDirection provides layoutDirection
    ) {
        content()
    }
}
