package com.psiphon3.azadi

import org.json.JSONArray
import org.json.JSONObject

data class TunnelStatistics(
    val connectedTunnelProtocol: String = "",
    val conduitStatusLine: String = "",
    val conduitStatusHistory: List<String> = emptyList(),
    val conduitStatusUpdatedAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("connectedTunnelProtocol", connectedTunnelProtocol)
        put("conduitStatusLine", conduitStatusLine)
        put("conduitStatusHistory", JSONArray(conduitStatusHistory))
        put("conduitStatusUpdatedAt", conduitStatusUpdatedAt)
    }

    companion object {
        const val MAX_HISTORY_STORED = 8
        const val MAX_HISTORY_UI = 4

        fun fromJson(obj: JSONObject): TunnelStatistics {
            val history = mutableListOf<String>()
            val arr = obj.optJSONArray("conduitStatusHistory")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optString(i)?.takeIf { it.isNotBlank() }?.let { history.add(it) }
                }
            }
            return TunnelStatistics(
                connectedTunnelProtocol = obj.optString("connectedTunnelProtocol", ""),
                conduitStatusLine = obj.optString("conduitStatusLine", ""),
                conduitStatusHistory = history,
                conduitStatusUpdatedAt = obj.optLong("conduitStatusUpdatedAt", 0L)
            )
        }

        fun empty(): TunnelStatistics = TunnelStatistics()
    }
}
