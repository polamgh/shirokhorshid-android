package com.psiphon3.azadi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class PublicGeoInfo(
    val ip: String = "—",
    val city: String = "",
    val countryName: String = "",
    val countryCode: String = ""
) {
    fun hasLocation(): Boolean = city.isNotBlank() || countryName.isNotBlank()

    fun isUsable(): Boolean = ip != "—" && ip.isNotBlank()
}

object PublicGeoLookup {
    private const val USER_AGENT = "AzadiTunnel/1.0"

    suspend fun lookup(): PublicGeoInfo = withContext(Dispatchers.IO) {
        var best = PublicGeoInfo()

        for (provider in currentIpProviders) {
            val candidate = provider()
            best = merge(best, candidate)
            if (best.isUsable() && best.hasLocation()) {
                return@withContext finalize(best)
            }
        }

        if (best.isUsable() && !best.hasLocation()) {
            best = merge(best, lookupByIp(best.ip))
        }

        if (!best.isUsable()) {
            best = merge(best, fetchIpOnly())
        }

        if (best.isUsable() && !best.hasLocation()) {
            best = merge(best, lookupByIp(best.ip))
        }

        finalize(best)
    }

    suspend fun lookupByIp(ip: String): PublicGeoInfo = withContext(Dispatchers.IO) {
        val trimmed = ip.trim()
        if (trimmed.isBlank() || trimmed == "—") return@withContext PublicGeoInfo()

        var best = PublicGeoInfo(ip = trimmed)
        for (provider in ipProviders(trimmed)) {
            best = merge(best, provider())
            if (best.hasLocation()) break
        }
        finalize(best)
    }

    private val currentIpProviders: List<() -> PublicGeoInfo> = listOf(
        { fetchIpWho("https://ipwho.is/") },
        { fetchFreeIpApi("https://free.freeipapi.com/api/json") },
        { fetchIpApiCo("https://ipapi.co/json/") }
    )

    private fun ipProviders(ip: String): List<() -> PublicGeoInfo> = listOf(
        { fetchIpWho("https://ipwho.is/$ip") },
        { fetchFreeIpApi("https://free.freeipapi.com/api/json/$ip") },
        { fetchIpApiCo("https://ipapi.co/$ip/json/") }
    )

    private fun fetchIpWho(url: String): PublicGeoInfo {
        val json = httpGetJson(url) ?: return PublicGeoInfo()
        if (json.optBoolean("success", true).not()) return PublicGeoInfo()
        return PublicGeoInfo(
            ip = json.optString("ip"),
            city = json.optString("city"),
            countryName = json.optString("country"),
            countryCode = json.optString("country_code")
        )
    }

    private fun fetchFreeIpApi(url: String): PublicGeoInfo {
        val json = httpGetJson(url) ?: return PublicGeoInfo()
        return PublicGeoInfo(
            ip = json.optString("ipAddress"),
            city = json.optString("cityName"),
            countryName = json.optString("countryName"),
            countryCode = json.optString("countryCode")
        )
    }

    private fun fetchIpApiCo(url: String): PublicGeoInfo {
        val json = httpGetJson(url) ?: return PublicGeoInfo()
        if (json.has("error")) return PublicGeoInfo()
        return PublicGeoInfo(
            ip = json.optString("ip"),
            city = json.optString("city"),
            countryName = json.optString("country_name"),
            countryCode = json.optString("country_code")
        )
    }

    private fun fetchIpOnly(): PublicGeoInfo {
        val json = httpGetJson("https://api.ipify.org?format=json")
        val ip = json?.optString("ip").orEmpty()
        return if (ip.isNotBlank()) PublicGeoInfo(ip = ip) else PublicGeoInfo()
    }

    private fun httpGetJson(url: String): JSONObject? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            conn.disconnect()
            if (body.isBlank()) null else JSONObject(body)
        } catch (_: Exception) {
            conn.disconnect()
            null
        }
    }

    private fun merge(current: PublicGeoInfo, incoming: PublicGeoInfo): PublicGeoInfo {
        return PublicGeoInfo(
            ip = incoming.ip.takeIf { it.isNotBlank() && it != "—" } ?: current.ip,
            city = incoming.city.takeIf { it.isNotBlank() } ?: current.city,
            countryName = incoming.countryName.takeIf { it.isNotBlank() } ?: current.countryName,
            countryCode = incoming.countryCode.takeIf { it.isNotBlank() } ?: current.countryCode
        )
    }

    private fun finalize(info: PublicGeoInfo): PublicGeoInfo {
        val ip = info.ip.trim().ifBlank { "—" }
        val city = info.city.trim()
        val code = info.countryCode.trim().uppercase(Locale.US)
        val country = localizedCountryName(info.countryName.trim(), code)
        return PublicGeoInfo(ip = ip, city = city, countryName = country, countryCode = code)
    }

    private fun localizedCountryName(countryName: String, countryCode: String): String {
        if (countryCode.length == 2) {
            val localized = Locale("", countryCode).getDisplayCountry(Locale.getDefault())
            if (localized.isNotBlank()) return localized
        }
        return countryName
    }
}
