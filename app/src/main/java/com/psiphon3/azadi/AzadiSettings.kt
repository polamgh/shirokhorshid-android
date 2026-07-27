package com.psiphon3.azadi

data class AzadiSettings(
    val egressRegion: String = "",
    val upstreamProxyEnabled: Boolean = false,
    val upstreamProxyHost: String = "",
    val upstreamProxyPort: Int = 8080,
    val upstreamProxyUseSystem: Boolean = false,
    val upstreamProxyUsername: String = "",
    val upstreamProxyPassword: String = "",
    val protocolSelection: String = "auto",
    val conduitMode: String = "public",
    val conduitTimeoutSeconds: Int = 180,
    val rejectCensoredCountryProxies: Boolean = true,
    val cdnFrontingCustomIpList: String = "",
    val cdnFrontingCustomSni: String = "",
    val cdnFrontingUseBuiltInScan: Boolean = true,
    val beastModeEnabled: Boolean = true,
    val smartFallbackChainEnabled: Boolean = true,
    val disableTimeouts: Boolean = false,
    val autoReconnect: Boolean = true,
    val connectOnLaunch: Boolean = false,
    val vpnOnDemandEnabled: Boolean = false,
    val vpnOnDemandMode: String = "always",
    val preferredLanguage: String = "system",
    val hasAcceptedConnectionDisclaimer: Boolean = false,
    val hasAcceptedVPNDisclosure: Boolean = false,
    val hasCompletedOnboarding: Boolean = false,
    val hasChosenLanguage: Boolean = false,
    val proxyOnlyModeEnabled: Boolean = false,
    val shareProxyOnLocalNetworkEnabled: Boolean = false,
    val lanHttpProxyPort: Int = 8087,
    val lanSocksProxyPort: Int = 1088,
    val lanProxyAuthEnabled: Boolean = false,
    val lanProxyUsername: String = "",
    val lanProxyPassword: String = "",
    val bypassIranIPsEnabled: Boolean = false,
    val bypassCustomRoutes: String = "",
    val bypassDomains: String = "",
    val bypassStrictModeEnabled: Boolean = false,
    val secureDNSMode: String = "doh",
    val secureDNSProvider: String = "google",
    val customDoHURL: String = "",
    val customDoTHost: String = "",
    val blockCleartextDNS: Boolean = false
)

enum class ReconnectMode {
    NONE,
    SOFT, // Restart tunnel only
    HARD  // Restart full service
}

enum class SettingsDestination {
    ROOT,
    LOGS,
    BYPASS_IRAN,
    SECURE_DNS,
    PROXY_ONLY,
    SHARE_PROXY,
    UPSTREAM_PROXY,
    ABOUT,
    LEGAL,
    PRIVACY,
    FULL_LICENSE,
    SUPPORT
}
