package com.psiphon3.azadi

import org.json.JSONObject

object ConnectedTunnelProtocolParser {

    @JvmStatic
    fun extractProtocol(message: String): String? {
        if (!message.contains("ConnectedServer", ignoreCase = true)) return null
        val jsonStart = message.indexOf('{')
        if (jsonStart < 0) return null
        return try {
            val root = JSONObject(message.substring(jsonStart))
            val data = if (root.has("data")) root.getJSONObject("data") else root
            data.optString("protocol", "").takeIf { it.isNotBlank() }
                ?: root.optString("protocol", "").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun displayName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val upper = raw.uppercase()

        if (upper.startsWith("INPROXY-WEBRTC-")) {
            val inner = raw.substring("INPROXY-WEBRTC-".length).trim()
            val innerLabel = if (inner.isBlank()) "" else displayName(inner)
            return if (innerLabel.isBlank()) "Conduit" else "Conduit · $innerLabel"
        }

        return when {
            upper == "SSH" || upper == "OSSH" -> "SSH"
            upper == "TLS-OSSH" -> "TLS"
            upper == "QUIC-OSSH" -> "QUIC"
            upper == "SHADOWSOCKS-OSSH" -> "Shadowsocks"
            upper in setOf(
                "FRONTED-MEEK-OSSH",
                "FRONTED-MEEK-HTTP-OSSH",
                "FRONTED-MEEK-QUIC-OSSH"
            ) -> "Meek"
            upper in setOf(
                "FRONTED-MEEK-CDN-OSSH",
                "FRONTED-MEEK-CDN-HTTP-OSSH",
                "FRONTED-MEEK-CDN-QUIC-OSSH"
            ) -> "CDN Meek"
            upper.startsWith("UNFRONTED-MEEK-") -> "Meek"
            upper.endsWith("-OSSH") -> upper.removeSuffix("-OSSH")
                .replace('_', ' ')
                .lowercase()
                .split(' ')
                .joinToString(" ") { word ->
                    word.replaceFirstChar { c -> c.uppercase() }
                }
            else -> raw
        }
    }
}
