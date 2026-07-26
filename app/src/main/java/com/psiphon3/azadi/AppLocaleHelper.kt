package com.psiphon3.azadi

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import com.psiphon3.psiphonlibrary.LocaleManager

object AppLocaleHelper {
    fun persist(context: Context, languageCode: String) {
        val code = languageCode.ifEmpty { "system" }
        LocaleManager.getInstance(context).applyLanguage(context, code)
    }

    @Suppress("DEPRECATION")
    fun applyToActivity(activity: Activity, languageCode: String) {
        val code = languageCode.ifEmpty { "system" }
        val localeManager = LocaleManager.getInstance(activity)
        localeManager.applyLanguage(activity, code)
        val localized = localeManager.wrapWithLanguage(activity, code)
        val config = Configuration(localized.resources.configuration)
        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
    }

    fun currentLanguage(context: Context): String {
        return LocaleManager.getInstance(context).getLanguage().ifEmpty { "system" }
    }
}
