package com.psiphon3.azadi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class QualityReport(
    val protocol: String = "",
    val publicIp: String = "",
    val region: String = "",
    val city: String = "",
    val countryName: String = "",
    val latencyMs: Long = -1,
    val https204Ok: Boolean = false
) {
    fun locationLine(): String? {
        val cityTrim = city.trim()
        val countryTrim = countryName.trim()
        return when {
            cityTrim.isNotEmpty() && countryTrim.isNotEmpty() -> "$cityTrim, $countryTrim"
            cityTrim.isNotEmpty() -> cityTrim
            countryTrim.isNotEmpty() -> countryTrim
            region.trim().isNotEmpty() -> region.trim()
            else -> null
        }
    }
}

data class LeakTestResult(
    val level: String,
    val summary: String
)

data class DiagnosticsResult(
    val internetOk: Boolean,
    val publicIp: String,
    val country: String,
    val leak: LeakTestResult,
    val quality: QualityReport
)

object PostConnectDiagnostics {
    suspend fun run(
        protocol: String?,
        region: String?
    ): DiagnosticsResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        var https204 = false

        val geo = PublicGeoLookup.lookup()
        var ip = geo.ip
        var city = geo.city
        var country = geo.countryName

        if (geo.isUsable() && !geo.hasLocation()) {
            val enriched = PublicGeoLookup.lookupByIp(geo.ip)
            if (enriched.hasLocation()) {
                ip = enriched.ip.ifBlank { ip }
                city = enriched.city.ifBlank { city }
                country = enriched.countryName.ifBlank { country }
            }
        }

        try {
            val probe = URL("https://www.google.com/generate_204").openConnection() as HttpURLConnection
            probe.connectTimeout = 8000
            probe.readTimeout = 8000
            probe.requestMethod = "GET"
            https204 = probe.responseCode == 204
            probe.disconnect()
        } catch (_: Exception) {
            https204 = false
        }

        val latency = System.currentTimeMillis() - start
        val internetOk = ip != "—" && https204

        if (internetOk) {
            AzadiEventLogger.logSync("INTERNET_TEST_PASSED", "ip=$ip")
        } else {
            AzadiEventLogger.logSync("INTERNET_TEST_FAILED")
        }

        val leak = evaluateLeak(ip)
        val quality = QualityReport(
            protocol = protocol.orEmpty(),
            publicIp = ip,
            region = region.orEmpty(),
            city = city,
            countryName = country,
            latencyMs = latency,
            https204Ok = https204
        )

        if (quality.https204Ok && quality.publicIp.isNotEmpty()) {
            AzadiEventLogger.logSync("QUALITY_REPORT_OK", quality.toLogLine())
        } else {
            AzadiEventLogger.logSync("QUALITY_REPORT_FAILED")
        }

        DiagnosticsResult(internetOk, ip, country, leak, quality)
    }

    suspend fun measurePingMs(): Long = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        return@withContext try {
            val conn = URL("https://www.google.com/generate_204").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "HEAD"
            conn.responseCode
            conn.disconnect()
            System.currentTimeMillis() - start
        } catch (_: Exception) {
            -1L
        }
    }

    private fun evaluateLeak(publicIp: String): LeakTestResult {
        val level = when {
            publicIp == "—" -> "WARNING"
            publicIp.startsWith("10.") || publicIp.startsWith("192.168.") -> "LEAK"
            else -> "SAFE"
        }
        val summary = when (level) {
            "SAFE" -> "No obvious IP leak detected"
            "LEAK" -> "Private-range IP observed — possible leak"
            else -> "Could not verify leak status"
        }
        AzadiEventLogger.logSync(
            when (level) {
                "SAFE" -> "LEAK_TEST_SAFE"
                "LEAK" -> "LEAK_TEST_LEAK"
                else -> "LEAK_TEST_WARNING"
            },
            summary
        )
        return LeakTestResult(level, summary)
    }

    private fun QualityReport.toLogLine(): String =
        "protocol=$protocol ip=$publicIp region=$region latencyMs=$latencyMs https204=$https204Ok"
}
