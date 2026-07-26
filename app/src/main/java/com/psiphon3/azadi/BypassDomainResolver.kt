package com.psiphon3.azadi

import android.content.Context
import net.grandcentrix.tray.AppPreferences
import org.json.JSONObject
import java.net.InetAddress

object BypassDomainResolver {
    private const val CACHE_KEY = "bypass_domain_resolved_ips"

    @JvmStatic
    fun resolveAndCache(context: Context, domainsBlob: String): Int {
        val domains = BypassRoutes.tokenize(domainsBlob)
            .filter { !it.contains('/') && it.contains('.') }
        if (domains.isEmpty()) return 0

        val cache = loadCache(context)
        var added = 0
        for (domain in domains) {
            try {
                val ips = InetAddress.getAllByName(domain)
                    .mapNotNull { it.hostAddress }
                    .filter { it.contains('.') && !it.contains(':') }
                    .distinct()
                if (ips.isNotEmpty()) {
                    cache[domain] = ips.joinToString(",")
                    added += ips.size
                    AzadiEventLogger.logSync("BYPASS_DOMAIN_RESOLVED", "domain=$domain count=${ips.size}")
                }
            } catch (e: Exception) {
                AzadiEventLogger.logSync("BYPASS_DOMAIN_FAILED", "domain=$domain error=${e.message}")
            }
        }
        saveCache(context, cache)
        return added
    }

    @JvmStatic
    fun resolvedRoutes(context: Context): List<BypassRoute> {
        val cache = loadCache(context)
        val routes = mutableListOf<BypassRoute>()
        val seen = mutableSetOf<BypassRoute>()
        for (ips in cache.values) {
            for (ip in ips.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }) {
                val route = BypassRoutes.parse(ip) ?: continue
                if (seen.add(route)) routes.add(route)
            }
        }
        return routes
    }

    private fun loadCache(context: Context): MutableMap<String, String> {
        val raw = AppPreferences(context.applicationContext).getString(CACHE_KEY, null) ?: return mutableMapOf()
        return try {
            val json = JSONObject(raw)
            val map = mutableMapOf<String, String>()
            json.keys().forEach { key ->
                json.optString(key)?.takeIf { it.isNotBlank() }?.let { map[key] = it }
            }
            map
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveCache(context: Context, cache: Map<String, String>) {
        val json = JSONObject()
        cache.forEach { (k, v) -> json.put(k, v) }
        AppPreferences(context.applicationContext).put(CACHE_KEY, json.toString())
    }
}
