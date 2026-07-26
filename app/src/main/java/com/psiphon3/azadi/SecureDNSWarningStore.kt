package com.psiphon3.azadi

import android.content.Context
import net.grandcentrix.tray.AppPreferences

object SecureDNSWarningStore {
    private const val KEY = "secure_dns_warning"

    fun get(context: Context): String? =
        AppPreferences(context.applicationContext).getString(KEY, null)?.takeIf { it.isNotEmpty() }

    fun set(context: Context, warning: String?) {
        val prefs = AppPreferences(context.applicationContext)
        if (warning.isNullOrEmpty()) {
            prefs.remove(KEY)
        } else {
            prefs.put(KEY, warning)
        }
    }
}
