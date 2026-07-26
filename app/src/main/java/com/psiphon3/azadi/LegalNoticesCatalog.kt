package com.psiphon3.azadi

import android.content.Context
import java.io.IOException

data class OpenSourceComponent(
    val id: String,
    val name: String,
    val license: String,
    val sourceUrl: String,
    val description: String,
    val licenseNeedsVerification: Boolean = false
)

object LegalNoticesCatalog {
    val components: List<OpenSourceComponent> = listOf(
        OpenSourceComponent(
            id = "psiphon-inc",
            name = "Psiphon (Psiphon Inc.)",
            license = "GNU General Public License v3 (tunnel-core components)",
            sourceUrl = "https://github.com/psiphon-inc",
            description = "Psiphon tunnel technology. Psiphon® is a registered trademark of Psiphon Inc. AzadiTunnel is not affiliated with Psiphon Inc."
        ),
        OpenSourceComponent(
            id = "psiphon-tunnel-core",
            name = "psiphon-tunnel-core",
            license = "GNU General Public License v3",
            sourceUrl = "https://github.com/azaditunnel/psiphon-tunnel-core",
            description = "Tunnel client core used by the VPN service (pinned fork/build)."
        ),
        OpenSourceComponent(
            id = "azadi-reference",
            name = "Azadi Tunnel reference implementation",
            license = "See upstream repositories",
            sourceUrl = "https://github.com/azaditunnel/azaditunnel-android",
            description = "Transport, CDN fronting, and Conduit behavior referenced for parity.",
            licenseNeedsVerification = true
        ),
        OpenSourceComponent(
            id = "tun2socks",
            name = "tun2socks",
            license = "License: needs verification",
            sourceUrl = "https://github.com/zhuhaow/tun2socks",
            description = "Forwards packet tunnel traffic to the local Psiphon SOCKS proxy.",
            licenseNeedsVerification = true
        ),
        OpenSourceComponent(
            id = "geolite2-country",
            name = "GeoLite2 Country database (MMDB)",
            license = "MaxMind GeoLite2 License (needs verification)",
            sourceUrl = "https://dev.maxmind.com/geoip/geolite2-free-geolocation-data",
            description = "Optional GeoIP database for Conduit country filtering when bundled.",
            licenseNeedsVerification = true
        )
    )

    fun readAsset(context: Context, path: String): String? = try {
        context.assets.open(path).bufferedReader().use { it.readText() }.takeIf { it.isNotBlank() }
    } catch (_: IOException) {
        null
    }

    fun appLicenseText(context: Context): String =
        readAsset(context, "legal/LICENSE").orEmpty()

    fun thirdPartyNoticesText(context: Context): String =
        readAsset(context, "legal/THIRD_PARTY_NOTICES.md").orEmpty()

    fun fullLicenseNoticesText(context: Context): String {
        val license = appLicenseText(context)
        val notices = thirdPartyNoticesText(context)
        if (license.isEmpty() && notices.isEmpty()) {
            return "Full license notices are not available in this build. See the project repository for LICENSE and THIRD_PARTY_NOTICES.md."
        }
        return buildList {
            if (notices.isNotEmpty()) add("=== PSIPHON / THIRD-PARTY NOTICES ===\n\n$notices")
            if (license.isNotEmpty()) add("=== AzadiTunnel LICENSE ===\n\n$license")
        }.joinToString("\n\n")
    }

    fun appLicensePreview(context: Context, maxChars: Int = 600): String {
        val text = appLicenseText(context)
        if (text.isEmpty()) return ""
        return if (text.length <= maxChars) text else text.take(maxChars) + "\n…"
    }

    fun logMissingComponentsOnOpen(context: Context) {
        components.filter { it.licenseNeedsVerification }.forEach { component ->
            AzadiEventLogger.logSync(
                "LICENSE_COMPONENT_MISSING",
                "name=${component.id} reason=license_needs_verification"
            )
        }
        if (appLicenseText(context).isEmpty()) {
            AzadiEventLogger.logSync("LICENSE_COMPONENT_MISSING", "name=LICENSE")
        }
        if (thirdPartyNoticesText(context).isEmpty()) {
            AzadiEventLogger.logSync("LICENSE_COMPONENT_MISSING", "name=THIRD_PARTY_NOTICES")
        }
    }
}
