package com.psiphon3.azadi

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

object LocalNetworkAddress {
    @JvmStatic
    fun wifiIpv4(context: Context): String? {
        return NetworkUtils.wifiIpv4Address(context)
    }
}
