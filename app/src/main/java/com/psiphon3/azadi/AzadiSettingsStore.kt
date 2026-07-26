package com.psiphon3.azadi

import android.content.Context
import com.psiphon3.R
import com.psiphon3.psiphonlibrary.EmbeddedValues
import com.psiphon3.psiphonlibrary.PsiphonConstants
import com.psiphon3.psiphonlibrary.UpstreamProxySettings
import net.grandcentrix.tray.AppPreferences

class AzadiSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = AppPreferences(appContext)

    private fun key(resId: Int) = appContext.getString(resId)

    fun load(): AzadiSettings {
        migrateSecureDnsDefaults()
        migrateLegalFlags()
        return loadInternal()
    }

    /** Legacy VPN disclosure acceptance implies connection disclaimer on older installs. */
    private fun migrateLegalFlags() {
        val disclaimerKey = key(R.string.hasAcceptedConnectionDisclaimerPreference)
        val vpnDisclosureKey = key(R.string.hasAcceptedVPNDisclosurePreference)
        val legacyKey = key(R.string.vpnServiceDataCollectionDisclosureAccepted)
        var disclaimer = prefs.getBoolean(disclaimerKey, false)
        var vpnDisclosure = prefs.getBoolean(vpnDisclosureKey, false)
        if (!disclaimer && prefs.getBoolean(legacyKey, false)) {
            disclaimer = true
            vpnDisclosure = true
            prefs.put(disclaimerKey, true)
            prefs.put(vpnDisclosureKey, true)
        } else if (disclaimer && !vpnDisclosure) {
            vpnDisclosure = true
            prefs.put(vpnDisclosureKey, true)
        }
    }

    /** Upgrade legacy off/cloudflare defaults to DoH Google. */
    private fun migrateSecureDnsDefaults() {
        val modeKey = key(R.string.secureDNSModePreference)
        val providerKey = key(R.string.secureDNSProviderPreference)
        val mode = prefs.getString(modeKey, "doh").orEmpty()
        val provider = prefs.getString(providerKey, "google").orEmpty()
        if (mode == "off" && provider == "cloudflare") {
            prefs.put(modeKey, "doh")
            prefs.put(providerKey, "google")
        }
    }

    private fun loadInternal(): AzadiSettings = AzadiSettings(
        egressRegion = prefs.getString(key(R.string.egressRegionPreference), PsiphonConstants.REGION_CODE_ANY).orEmpty(),
        upstreamProxyEnabled = UpstreamProxySettings.getUseHTTPProxy(appContext),
        upstreamProxyHost = UpstreamProxySettings.getCustomProxyHost(appContext) ?: "",
        upstreamProxyPort = UpstreamProxySettings.getCustomProxyPort(appContext)?.toIntOrNull() ?: 8080,
        upstreamProxyUseSystem = UpstreamProxySettings.getUseSystemProxySettings(appContext),
        upstreamProxyUsername = UpstreamProxySettings.getProxyUsername(appContext) ?: "",
        upstreamProxyPassword = UpstreamProxySettings.getProxyPassword(appContext) ?: "",
        protocolSelection = prefs.getString(key(R.string.protocolSelectionPreference), "auto").orEmpty().ifEmpty { "auto" },
        conduitMode = prefs.getString(key(R.string.conduitModePreference), "public").orEmpty().ifEmpty { "public" },
        conduitTimeoutSeconds = prefs.getString(key(R.string.conduitTimeoutPreference), "180").orEmpty().toIntOrNull() ?: 180,
        rejectCensoredCountryProxies = prefs.getBoolean(key(R.string.rejectCensoredCountryProxiesPreference), true),
        cdnFrontingCustomIpList = prefs.getString(key(R.string.cdnFrontingCustomIpListPreference), "").orEmpty(),
        cdnFrontingCustomSni = prefs.getString(key(R.string.cdnFrontingCustomSniPreference), "").orEmpty(),
        cdnFrontingUseBuiltInScan = prefs.getBoolean(key(R.string.cdnFrontingUseBuiltInScanPreference), true),
        beastModeEnabled = prefs.getBoolean(key(R.string.beastModePreference), true),
        smartFallbackChainEnabled = prefs.getBoolean(key(R.string.smartFallbackChainPreference), true),
        disableTimeouts = prefs.getBoolean(key(R.string.disableTimeoutsPreference), false),
        autoReconnect = prefs.getBoolean(key(R.string.autoReconnectPreference), true),
        connectOnLaunch = prefs.getBoolean(key(R.string.connectOnLaunchPreference), false),
        vpnOnDemandEnabled = prefs.getBoolean(key(R.string.vpnOnDemandEnabledPreference), false),
        vpnOnDemandMode = prefs.getString(key(R.string.vpnOnDemandModePreference), "always").orEmpty().ifEmpty { "always" },
        preferredLanguage = prefs.getString(key(R.string.preferenceLanguageSelection), "system").orEmpty().ifEmpty { "system" },
        hasAcceptedConnectionDisclaimer = prefs.getBoolean(key(R.string.hasAcceptedConnectionDisclaimerPreference), false),
        hasAcceptedVPNDisclosure = prefs.getBoolean(key(R.string.hasAcceptedVPNDisclosurePreference), false),
        hasCompletedOnboarding = prefs.getBoolean(key(R.string.hasCompletedOnboardingPreference), false),
        hasChosenLanguage = prefs.getBoolean(key(R.string.hasChosenLanguagePreference), false),
        proxyOnlyModeEnabled = prefs.getBoolean(key(R.string.proxyOnlyModePreference), false),
        shareProxyOnLocalNetworkEnabled = prefs.getBoolean(key(R.string.shareProxyOnNetworkPreference), false),
        lanHttpProxyPort = prefs.getString(key(R.string.shareProxyOnNetworkHttpPortPreference), "").orEmpty().toIntOrNull() ?: 8087,
        lanSocksProxyPort = prefs.getString(key(R.string.shareProxyOnNetworkSocksPortPreference), "").orEmpty().toIntOrNull() ?: 1088,
        bypassIranIPsEnabled = prefs.getBoolean(key(R.string.bypassIranIPsPreference), false),
        bypassCustomRoutes = prefs.getString(key(R.string.bypassCustomRoutesPreference), "").orEmpty(),
        bypassDomains = prefs.getString(key(R.string.bypassDomainsPreference), "").orEmpty(),
        bypassStrictModeEnabled = prefs.getBoolean(key(R.string.bypassStrictModePreference), false),
        secureDNSMode = prefs.getString(key(R.string.secureDNSModePreference), "doh").orEmpty().ifEmpty { "doh" },
        secureDNSProvider = prefs.getString(key(R.string.secureDNSProviderPreference), "google").orEmpty().ifEmpty { "google" },
        customDoHURL = prefs.getString(key(R.string.customDoHURLPreference), "").orEmpty(),
        customDoTHost = prefs.getString(key(R.string.customDoTHostPreference), "").orEmpty(),
        blockCleartextDNS = prefs.getBoolean(key(R.string.blockCleartextDNSPreference), false)
    )

    fun saveUpstreamProxy(settings: AzadiSettings) {
        prefs.put(key(R.string.useProxySettingsPreference), settings.upstreamProxyEnabled)
        prefs.put(key(R.string.useSystemProxySettingsPreference), settings.upstreamProxyUseSystem)
        prefs.put(key(R.string.useCustomProxySettingsPreference), !settings.upstreamProxyUseSystem)
        prefs.put(key(R.string.useCustomProxySettingsHostPreference), settings.upstreamProxyHost)
        prefs.put(key(R.string.useCustomProxySettingsPortPreference), settings.upstreamProxyPort.toString())
        prefs.put(key(R.string.useProxyAuthenticationPreference), settings.upstreamProxyUsername.isNotEmpty())
        prefs.put(key(R.string.useProxyUsernamePreference), settings.upstreamProxyUsername)
        prefs.put(key(R.string.useProxyPasswordPreference), settings.upstreamProxyPassword)
    }

    fun updateAppSettings(settings: AzadiSettings, logKey: String): AzadiSettings {
        save(settings)
        AzadiEventLogger.logSync("SETTING_CHANGED", logKey)
        return settings
    }

    fun save(settings: AzadiSettings) {
        saveUpstreamProxy(settings)
        prefs.put(key(R.string.egressRegionPreference), settings.egressRegion)
        prefs.put(key(R.string.protocolSelectionPreference), settings.protocolSelection)
        prefs.put(key(R.string.conduitModePreference), settings.conduitMode)
        prefs.put(key(R.string.conduitTimeoutPreference), settings.conduitTimeoutSeconds.toString())
        prefs.put(key(R.string.rejectCensoredCountryProxiesPreference), settings.rejectCensoredCountryProxies)
        prefs.put(key(R.string.cdnFrontingCustomIpListPreference), settings.cdnFrontingCustomIpList)
        prefs.put(key(R.string.cdnFrontingCustomSniPreference), settings.cdnFrontingCustomSni)
        prefs.put(key(R.string.cdnFrontingUseBuiltInScanPreference), settings.cdnFrontingUseBuiltInScan)
        prefs.put(key(R.string.beastModePreference), settings.beastModeEnabled)
        prefs.put(key(R.string.smartFallbackChainPreference), settings.smartFallbackChainEnabled)
        prefs.put(key(R.string.disableTimeoutsPreference), settings.disableTimeouts)
        prefs.put(key(R.string.autoReconnectPreference), settings.autoReconnect)
        prefs.put(key(R.string.connectOnLaunchPreference), settings.connectOnLaunch)
        prefs.put(key(R.string.vpnOnDemandEnabledPreference), settings.vpnOnDemandEnabled)
        prefs.put(key(R.string.vpnOnDemandModePreference), settings.vpnOnDemandMode)
        prefs.put(key(R.string.preferenceLanguageSelection), settings.preferredLanguage)
        prefs.put("language_key", settings.preferredLanguage)
        prefs.put(key(R.string.hasAcceptedConnectionDisclaimerPreference), settings.hasAcceptedConnectionDisclaimer)
        prefs.put(key(R.string.hasAcceptedVPNDisclosurePreference), settings.hasAcceptedVPNDisclosure)
        prefs.put(key(R.string.vpnServiceDataCollectionDisclosureAccepted), settings.hasAcceptedVPNDisclosure)
        prefs.put(key(R.string.hasCompletedOnboardingPreference), settings.hasCompletedOnboarding)
        prefs.put(key(R.string.hasChosenLanguagePreference), settings.hasChosenLanguage)
        prefs.put(key(R.string.proxyOnlyModePreference), settings.proxyOnlyModeEnabled)
        prefs.put(key(R.string.shareProxyOnNetworkPreference), settings.shareProxyOnLocalNetworkEnabled)
        prefs.put(key(R.string.shareProxyOnNetworkHttpPortPreference), settings.lanHttpProxyPort.toString())
        prefs.put(key(R.string.shareProxyOnNetworkSocksPortPreference), settings.lanSocksProxyPort.toString())
        prefs.put(key(R.string.bypassIranIPsPreference), settings.bypassIranIPsEnabled)
        prefs.put(key(R.string.bypassCustomRoutesPreference), settings.bypassCustomRoutes)
        prefs.put(key(R.string.bypassDomainsPreference), settings.bypassDomains)
        prefs.put(key(R.string.bypassStrictModePreference), settings.bypassStrictModeEnabled)
        prefs.put(key(R.string.secureDNSModePreference), settings.secureDNSMode)
        prefs.put(key(R.string.secureDNSProviderPreference), settings.secureDNSProvider)
        prefs.put(key(R.string.customDoHURLPreference), settings.customDoHURL)
        prefs.put(key(R.string.customDoTHostPreference), settings.customDoTHost)
        prefs.put(key(R.string.blockCleartextDNSPreference), settings.blockCleartextDNS)
    }

    fun saveField(settings: AzadiSettings, block: (AzadiSettings) -> AzadiSettings): AzadiSettings {
        val updated = block(settings)
        save(updated)
        return updated
    }

    fun hasActivePsiphonConfig(): Boolean =
        EmbeddedValues.REMOTE_SERVER_LIST_URLS_JSON.isNotEmpty() &&
                EmbeddedValues.REMOTE_SERVER_LIST_URLS_JSON != "[]"

    fun conduitConnectAllowed(): Boolean =
        !EmbeddedValues.CONDUIT_COMPARTMENT_ID.isNullOrEmpty()

    fun resetToDefaults(preserveOnboarding: AzadiSettings): AzadiSettings {
        val fresh = AzadiSettings(
            hasAcceptedConnectionDisclaimer = preserveOnboarding.hasAcceptedConnectionDisclaimer,
            hasAcceptedVPNDisclosure = preserveOnboarding.hasAcceptedVPNDisclosure,
            hasCompletedOnboarding = preserveOnboarding.hasCompletedOnboarding,
            hasChosenLanguage = preserveOnboarding.hasChosenLanguage,
            preferredLanguage = preserveOnboarding.preferredLanguage
        )
        save(fresh)
        return fresh
    }
}
