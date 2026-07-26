package com.psiphon3.azadi

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

object LocalNetworkAddress {
    @JvmStatic
    fun wifiIpv4(context: Context): String? {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        if (caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return null
        }

        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                val name = iface.name.lowercase()
                if (!name.startsWith("wlan") && !name.startsWith("wifi") && name != "en0") return@forEach
                iface.inetAddresses.toList().forEach { addr ->
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank() && !host.startsWith("169.254.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        return NetworkUtils.wifiIpv4Address(context)
    }
}
