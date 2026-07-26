package com.psiphon3.azadi

object SecureDNSConfiguration {
    @JvmStatic
    fun isActive(settings: AzadiSettings): Boolean = settings.secureDNSMode != "off"

    @JvmStatic
    fun dnsServerAddress(settings: AzadiSettings): String? {
        return when (settings.secureDNSMode) {
            "doh" -> when (settings.secureDNSProvider) {
                "google" -> "8.8.8.8"
                "cloudflare" -> "1.1.1.1"
                "quad9" -> "9.9.9.9"
                "adguard" -> "94.140.14.14"
                "custom" -> settings.customDoHURL.trim().takeIf { it.isNotEmpty() }?.let { "1.1.1.1" }
                else -> null
            }
            "dot" -> when (settings.secureDNSProvider) {
                "google" -> "8.8.8.8"
                "cloudflare" -> "1.1.1.1"
                "quad9" -> "9.9.9.9"
                "adguard" -> "94.140.14.14"
                else -> settings.customDoTHost.trim().takeIf { it.isNotEmpty() }
            }
            else -> null
        }
    }

    fun alternateDnsServers(settings: AzadiSettings): List<String> {
        val primary = dnsServerAddress(settings) ?: return emptyList()
        return alternateForProvider(settings.secureDNSProvider, primary)
    }

    @JvmStatic
    fun dnsServerFor(mode: String, provider: String, customDoTHost: String): String? {
        if (mode == "off") return null
        return when (mode) {
            "doh" -> when (provider) {
                "google" -> "8.8.8.8"
                "cloudflare" -> "1.1.1.1"
                "quad9" -> "9.9.9.9"
                "adguard" -> "94.140.14.14"
                else -> null
            }
            "dot" -> when (provider) {
                "google" -> "8.8.8.8"
                "cloudflare" -> "1.1.1.1"
                "quad9" -> "9.9.9.9"
                "adguard" -> "94.140.14.14"
                else -> customDoTHost.trim().takeIf { it.isNotEmpty() }
            }
            else -> null
        }
    }

    @JvmStatic
    fun alternateDnsServersFor(mode: String, provider: String, customDoTHost: String): List<String> {
        val primary = dnsServerFor(mode, provider, customDoTHost) ?: return emptyList()
        return alternateForProvider(provider, primary)
    }

    private fun alternateForProvider(provider: String, primary: String): List<String> =
        when (provider) {
            "cloudflare" -> listOf(primary, "1.0.0.1")
            "google" -> listOf(primary, "8.8.4.4")
            else -> listOf(primary)
        }
}
