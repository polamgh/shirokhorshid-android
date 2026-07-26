package com.psiphon3.azadi

data class BypassRoute(
    val address: String,
    val mask: String,
    val prefix: Int
) {
    val cidr: String get() = "$address/$prefix"
}

object BypassRoutes {
    fun tokenize(blob: String): List<String> =
        blob.split('\n', '\r', ',', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

    fun parse(entry: String): BypassRoute? {
        val trimmed = entry.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split('/', limit = 2)
        val ipPart = parts[0]
        val prefix = if (parts.size == 2) {
            parts[1].toIntOrNull()?.takeIf { it in 0..32 } ?: return null
        } else {
            32
        }
        val octets = ipv4Octets(ipPart) ?: return null
        val raw = (octets[0].toLong() shl 24) or
                (octets[1].toLong() shl 16) or
                (octets[2].toLong() shl 8) or
                octets[3].toLong()
        val maskValue = prefixToMask(prefix)
        val network = raw and maskValue
        return BypassRoute(
            address = dotted(network),
            mask = dotted(maskValue),
            prefix = prefix
        )
    }

    fun parseList(entries: List<String>): List<BypassRoute> {
        val seen = mutableSetOf<BypassRoute>()
        val result = mutableListOf<BypassRoute>()
        for (entry in entries) {
            val route = parse(entry) ?: continue
            if (seen.add(route)) result.add(route)
        }
        return result
    }

    @JvmStatic
    fun parseBlob(blob: String): List<BypassRoute> = parseList(tokenize(blob))

    private fun ipv4Octets(ip: String): List<Int>? {
        val comps = ip.split('.')
        if (comps.size != 4) return null
        val octets = mutableListOf<Int>()
        for (c in comps) {
            val v = c.toIntOrNull() ?: return null
            if (v !in 0..255) return null
            octets.add(v)
        }
        return octets
    }

    private fun dotted(value: Long): String {
        return "${(value shr 24) and 0xFF}.${(value shr 16) and 0xFF}.${(value shr 8) and 0xFF}.${value and 0xFF}"
    }

    private fun prefixToMask(prefix: Int): Long {
        if (prefix == 0) return 0L
        if (prefix == 32) return 0xFFFFFFFFL
        return (0xFFFFFFFFL shl (32 - prefix))
    }
}
