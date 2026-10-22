package com.psiphon3.azadi

import android.content.Context

/**
 * Where the device is before it connects (the "origin" of the dashboard map route). Captured
 * while disconnected, so it reflects the real network rather than the tunnel's egress.
 */
object OriginGeoStore {
    private const val PREFS = "azadi_origin_geo"

    data class Origin(
        val ip: String,
        val countryCode: String,
        val latitude: Double?,
        val longitude: Double?
    )

    fun load(context: Context): Origin? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ip = prefs.getString("ip", null) ?: return null
        val lat = prefs.getString("lat", null)?.toDoubleOrNull()
        val lon = prefs.getString("lon", null)?.toDoubleOrNull()
        return Origin(ip, prefs.getString("cc", "").orEmpty(), lat, lon)
    }

    fun save(context: Context, info: PublicGeoInfo) {
        if (!info.isUsable()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("ip", info.ip)
            .putString("cc", info.countryCode)
            .putString("lat", if (info.hasCoordinates()) info.latitude.toString() else null)
            .putString("lon", if (info.hasCoordinates()) info.longitude.toString() else null)
            .apply()
    }
}
