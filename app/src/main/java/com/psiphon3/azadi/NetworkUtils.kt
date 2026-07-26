package com.psiphon3.azadi

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun wifiIpv4Address(context: Context): String? {
        if (!isOnWifi(context)) return null
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.flatMap { it.inetAddresses.toList() }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }

    suspend fun measureDownloadSpeedMbps(): Double {
        return try {
            val url = java.net.URL("https://speed.cloudflare.com/__down?bytes=1000000")
            val start = System.currentTimeMillis()
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            val bytes = conn.inputStream.readBytes()
            conn.disconnect()
            val seconds = (System.currentTimeMillis() - start).coerceAtLeast(1) / 1000.0
            (bytes.size * 8.0) / seconds / 1_000_000.0
        } catch (_: Exception) {
            0.0
        }
    }

    fun hasInternetConnectivity(): Boolean = try {
        val conn = java.net.URL("https://www.google.com/generate_204")
            .openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.requestMethod = "HEAD"
        val code = conn.responseCode
        conn.disconnect()
        code in 200..399
    } catch (_: Exception) {
        false
    }
}
