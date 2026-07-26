package com.psiphon3.azadi

import android.content.Context
import net.grandcentrix.tray.AppPreferences
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object IranBypassListService {
    private const val CACHE_FILE = "bypass-iran-cidr.txt"
    private const val PREF_COUNT = "bypass_iran_list_count"
    private const val PREF_UPDATED_AT = "bypass_iran_list_updated_at"
    private const val TTL_MS = 24L * 60L * 60L * 1000L

    private val remoteSources = listOf(
        "https://cdn.jsdelivr.net/gh/ipverse/rir-ip@master/country/ir/ipv4-aggregated.txt",
        "https://www.iwik.org/ipcountry/IR.cidr",
        "https://raw.githubusercontent.com/ipverse/rir-ip/master/country/ir/ipv4-aggregated.txt"
    )

    @JvmStatic
    fun effectiveRoutes(context: Context): List<BypassRoute> {
        val cached = readCacheFile(context)
        if (cached.isNotEmpty()) return cached
        return bundledRoutes(context)
    }

    @JvmStatic
    fun listCount(context: Context): Int = effectiveRoutes(context).size

    @JvmStatic
    fun lastUpdatedAt(context: Context): Long =
        AppPreferences(context.applicationContext).getLong(PREF_UPDATED_AT, 0L)

    @JvmStatic
    fun refresh(context: Context, force: Boolean): Int {
        val appContext = context.applicationContext
        val prefs = AppPreferences(appContext)
        val last = prefs.getLong(PREF_UPDATED_AT, 0L)
        if (!force && last > 0 && System.currentTimeMillis() - last < TTL_MS) {
            val count = listCount(appContext)
            AzadiEventLogger.logSync("BYPASS_IRAN_LIST_CACHE_USED", "count=$count source=cache")
            return count
        }

        AzadiEventLogger.logSync("BYPASS_IRAN_LIST_FETCH_STARTED")
        for (source in remoteSources) {
            try {
                val body = httpGet(source)
                val routes = parseRemoteBody(body)
                if (routes.isNotEmpty()) {
                    writeCacheFile(appContext, routes)
                    prefs.put(PREF_COUNT, routes.size)
                    prefs.put(PREF_UPDATED_AT, System.currentTimeMillis())
                    AzadiEventLogger.logSync("BYPASS_IRAN_LIST_FETCH_OK", "count=${routes.size}")
                    return routes.size
                }
            } catch (e: Exception) {
                AzadiEventLogger.logSync("BYPASS_IRAN_LIST_FETCH_FAILED", "source=$source error=${e.message}")
            }
        }

        val fallback = bundledRoutes(appContext)
        if (fallback.isNotEmpty()) {
            writeCacheFile(appContext, fallback)
            prefs.put(PREF_COUNT, fallback.size)
            if (prefs.getLong(PREF_UPDATED_AT, 0L) == 0L) {
                prefs.put(PREF_UPDATED_AT, System.currentTimeMillis())
            }
            AzadiEventLogger.logSync("BYPASS_IRAN_LIST_CACHE_USED", "count=${fallback.size} source=bundled")
            return fallback.size
        }

        AzadiEventLogger.logSync("BYPASS_IRAN_NO_LIST_WARNING")
        return 0
    }

    private fun readCacheFile(context: Context): List<BypassRoute> {
        val file = File(context.filesDir, CACHE_FILE)
        if (!file.exists()) return emptyList()
        return BypassRoutes.parseBlob(file.readText())
    }

    private fun writeCacheFile(context: Context, routes: List<BypassRoute>) {
        val text = routes.joinToString("\n") { it.cidr }
        File(context.filesDir, CACHE_FILE).writeText(text)
    }

    private fun bundledRoutes(context: Context): List<BypassRoute> {
        return try {
            val text = context.assets.open("bypass-iran-cidr-bundled.txt").bufferedReader().readText()
            BypassRoutes.parseBlob(text)
        } catch (_: Exception) {
            BundledIranCIDR.routes
        }
    }

    private fun parseRemoteBody(body: String): List<BypassRoute> {
        val lines = body.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                if (line.contains('/')) line else "$line/32"
            }
        return BypassRoutes.parseList(lines)
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "AzadiTunnel/1.0")
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            conn.disconnect()
        }
    }
}
