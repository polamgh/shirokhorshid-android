package com.psiphon3.azadi

import android.content.Context
import android.content.SharedPreferences
import com.psiphon3.R
import com.psiphon3.psiphonlibrary.DisguiseManager
import com.psiphon3.psiphonlibrary.UpstreamProxySettings
import com.psiphon3.psiphonlibrary.VpnAppsUtils
import net.grandcentrix.tray.AppPreferences
import net.grandcentrix.tray.core.SharedPreferencesImport

enum class SettingsRestartMode { NONE, VPN, TUNNEL }

object SettingsPreferencesMigrator {
    fun migrateVpnSettings(context: Context, tray: AppPreferences) {
        val prefName = context.getString(R.string.moreOptionsPreferencesName)
        tray.migrate(
            SharedPreferencesImport(context, prefName, context.getString(R.string.preferenceIncludeAllAppsInVpn), context.getString(R.string.preferenceIncludeAllAppsInVpn)),
            SharedPreferencesImport(context, prefName, context.getString(R.string.preferenceIncludeAppsInVpn), context.getString(R.string.preferenceIncludeAppsInVpn)),
            SharedPreferencesImport(context, prefName, context.getString(R.string.preferenceIncludeAppsInVpnString), context.getString(R.string.preferenceIncludeAppsInVpnString)),
            SharedPreferencesImport(context, prefName, context.getString(R.string.preferenceExcludeAppsFromVpn), context.getString(R.string.preferenceExcludeAppsFromVpn)),
            SharedPreferencesImport(context, prefName, context.getString(R.string.preferenceExcludeAppsFromVpnString), context.getString(R.string.preferenceExcludeAppsFromVpnString))
        )
    }

    fun migrateProxySettings(context: Context, tray: AppPreferences) {
        val prefName = context.getString(R.string.moreOptionsPreferencesName)
        tray.migrate(
            SharedPreferencesImport(context, prefName, context.getString(R.string.useProxySettingsPreference), context.getString(R.string.useProxySettingsPreference)),
            SharedPreferencesImport(context, prefName, context.getString(R.string.useSystemProxySettingsPreference), context.getString(R.string.useSystemProxySettingsPreference)),
            SharedPreferencesImport(context, prefName, context.getString(R.string.useCustomProxySettingsPreference), context.getString(R.string.useCustomProxySettingsPreference)),
            SharedPreferencesImport(context, prefName, context.getString(R.string.useCustomProxySettingsHostPreference), context.getString(R.string.useCustomProxySettingsHostPreference)),
            SharedPreferencesImport(context, prefName, context.getString(R.string.useCustomProxySettingsPortPreference), context.getString(R.string.useCustomProxySettingsPortPreference)),
            SharedPreferencesImport(context, prefName, context.getString(R.string.useProxyAuthenticationPreference), context.getString(R.string.useProxyAuthenticationPreference)),
            SharedPreferencesImport(context, prefName, context.getString(R.string.useProxyUsernamePreference), context.getString(R.string.useProxyUsernamePreference)),
            SharedPreferencesImport(context, prefName, context.getString(R.string.useProxyPasswordPreference), context.getString(R.string.useProxyPasswordPreference)),
            SharedPreferencesImport(context, prefName, context.getString(R.string.useProxyDomainPreference), context.getString(R.string.useProxyDomainPreference))
        )
    }

    fun vpnRestartRequired(context: Context, tray: AppPreferences): Boolean {
        val prefs = localPrefs(context)
        if (prefs.getBoolean(context.getString(R.string.preferenceIncludeAllAppsInVpn), true) !=
            tray.getBoolean(context.getString(R.string.preferenceIncludeAllAppsInVpn), true)
        ) return true
        if (prefs.getBoolean(context.getString(R.string.preferenceIncludeAppsInVpn), false) !=
            tray.getBoolean(context.getString(R.string.preferenceIncludeAppsInVpn), false)
        ) return true
        if (prefs.getBoolean(context.getString(R.string.preferenceExcludeAppsFromVpn), false) !=
            tray.getBoolean(context.getString(R.string.preferenceExcludeAppsFromVpn), false)
        ) return true
        return false
    }

    fun proxyRestartRequired(context: Context): Boolean {
        val prefs = localPrefs(context)
        val useProxy = prefs.getBoolean(context.getString(R.string.useProxySettingsPreference), false)
        if (useProxy != UpstreamProxySettings.getUseHTTPProxy(context)) return true
        if (!useProxy) return false
        val useCustom = prefs.getBoolean(context.getString(R.string.useCustomProxySettingsPreference), false)
        if (useCustom != UpstreamProxySettings.getUseCustomProxySettings(context)) return true
        return false
    }

    fun tunnelRestartRequired(context: Context, tray: AppPreferences): Boolean {
        val prefs = localPrefs(context)
        val keys = listOf(
            R.string.protocolSelectionPreference,
            R.string.cdnFrontingCustomIpListPreference,
            R.string.cdnFrontingCustomSniPreference,
            R.string.disableTimeoutsPreference,
            R.string.beastModePreference,
            R.string.conduitModePreference,
            R.string.conduitTimeoutPreference,
            R.string.rejectCensoredCountryProxiesPreference,
            R.string.shareProxyOnNetworkPreference,
            R.string.shareProxyOnNetworkSocksPortPreference,
            R.string.shareProxyOnNetworkHttpPortPreference,
            R.string.shareProxyOnNetworkUsernamePreference,
            R.string.shareProxyOnNetworkPasswordPreference,
            R.string.smartFallbackChainPreference,
            R.string.proxyOnlyModePreference,
            R.string.bypassIranIPsPreference,
            R.string.secureDNSModePreference
        )
        for (res in keys) {
            val k = context.getString(res)
            val local = prefs.all[k]
            val trayVal = tray.getString(k, null)
            when (local) {
                is Boolean -> if (local != tray.getBoolean(k, false)) return true
                is String -> if (local != tray.getString(k, "")) return true
            }
        }
        return false
    }

    private fun localPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(context.getString(R.string.moreOptionsPreferencesName), Context.MODE_PRIVATE)
}
