package com.psiphon3.azadi

import android.content.Context
import com.psiphon3.R
import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern

object ConduitStatusParser {

    private val candidateCountPattern =
        Pattern.compile("CandidateServers.*?(\\d+)\\s+candidates?", Pattern.CASE_INSENSITIVE)
    private val candidateDurationPattern =
        Pattern.compile("\\((\\d+(?:\\.\\d+)?)(ms|s)\\)", Pattern.CASE_INSENSITIVE)
    private val tryingRelayPattern =
        Pattern.compile("trying Conduit relay \\(country:\\s*([A-Z]{2})\\)", Pattern.CASE_INSENSITIVE)
    private val connectedRelayCountryPattern = Pattern.compile(
        "tunnel connected via Conduit relay \\(protocol:\\s*([^,]+),\\s*country:\\s*([A-Z]{2})\\)",
        Pattern.CASE_INSENSITIVE
    )
    private val connectedRelayProtocolPattern = Pattern.compile(
        "tunnel connected via Conduit relay \\(protocol:\\s*([^)]+)\\)",
        Pattern.CASE_INSENSITIVE
    )

    @JvmStatic
    fun shouldParse(message: String): Boolean {
        val lower = message.lowercase(Locale.US)
        return lower.contains("inproxy") ||
            lower.contains("in-proxy") ||
            lower.contains("conduit relay") ||
            lower.contains("candidateservers") ||
            lower.contains("failed to make dial parameters") ||
            lower.contains("verifysignature") ||
            lower.contains("inproxy-dial:") ||
            lower.contains("requestingtactics") ||
            lower.contains("requestedtactics")
    }

    @JvmStatic
    fun parseDashboardLine(context: Context, message: String): String? {
        val lower = message.lowercase(Locale.US)

        if (lower.contains("verifysignature") &&
            (lower.contains("missing public key") || lower.contains("missing distributor"))
        ) {
            return context.getString(R.string.azadi_conduit_blocked)
        }
        if (lower.contains("failed to make dial parameters") && lower.contains("missing")) {
            return context.getString(R.string.azadi_conduit_blocked)
        }

        if (lower.contains("candidateservers")) {
            if (lower.contains("loading")) {
                return context.getString(R.string.azadi_conduit_loading_candidates)
            }
            val countMatch = candidateCountPattern.matcher(message)
            if (countMatch.find()) {
                val count = countMatch.group(1) ?: "0"
                val duration = extractDuration(message) ?: ""
                return if (duration.isNotBlank()) {
                    context.getString(R.string.azadi_conduit_loaded_candidates, count, duration)
                } else {
                    context.getString(R.string.azadi_conduit_loaded_candidates_no_duration, count)
                }
            }
        }

        tryingRelayPattern.matcher(message).let { matcher ->
            if (matcher.find()) {
                val country = countryName(context, matcher.group(1) ?: "")
                return context.getString(R.string.azadi_conduit_trying_relay, country)
            }
        }

        connectedRelayCountryPattern.matcher(message).let { matcher ->
            if (matcher.find()) {
                val protocol = ConnectedTunnelProtocolParser.displayName(matcher.group(1))
                val country = countryName(context, matcher.group(2) ?: "")
                return context.getString(R.string.azadi_conduit_connected_relay, protocol, country)
            }
        }

        connectedRelayProtocolPattern.matcher(message).let { matcher ->
            if (matcher.find()) {
                val protocol = ConnectedTunnelProtocolParser.displayName(matcher.group(1))
                return context.getString(R.string.azadi_conduit_connected_relay_no_country, protocol)
            }
        }

        if (lower.contains("inproxy: selected broker") || lower.contains("selected broker")) {
            return context.getString(R.string.azadi_conduit_broker_selected)
        }
        if (lower.contains("in-proxy protocol selection") || lower.contains("selecting in-proxy protocol")) {
            return context.getString(R.string.azadi_conduit_selecting_protocol)
        }
        if (lower.contains("broker 404")) {
            return context.getString(R.string.azadi_conduit_broker_404)
        }
        if (lower.contains("broker roundtripper") || lower.contains("contacting in-proxy broker")) {
            return context.getString(R.string.azadi_conduit_contacting_broker)
        }
        if (lower.contains("requestingtactics") || lower.contains("requestedtactics")) {
            return context.getString(R.string.azadi_conduit_waiting_tactics)
        }

        if (message.contains("inproxy-dial:", ignoreCase = true)) {
            return parseInproxyDialLine(message)
        }

        if (lower.contains("trying conduit relay") && message.contains("country:")) {
            tryingRelayPattern.matcher(message).let { matcher ->
                if (matcher.find()) {
                    val country = countryName(context, matcher.group(1) ?: "")
                    return context.getString(R.string.azadi_conduit_trying_relay, country)
                }
            }
        }

        return null
    }

    private fun parseInproxyDialLine(message: String): String? {
        try {
            var jsonStr = message
            val colonSpace = message.indexOf(": {")
            if (colonSpace >= 0) jsonStr = message.substring(colonSpace + 2)
            val data = JSONObject(jsonStr)
            val msg = data.optString("message", "")
            if (!msg.startsWith("inproxy-dial:", ignoreCase = true)) return null
            val phase = msg.substringAfter("inproxy-dial:").trim()
            val sb = StringBuilder()
            data.optString("attempt").takeIf { it.isNotBlank() }?.let { sb.append("#$it ") }
            sb.append(phase)
            formatDuration(data.optString("duration"))?.takeIf { it.isNotBlank() }?.let { sb.append(" ($it)") }
            formatDuration(data.optString("timeout"))?.takeIf { it.isNotBlank() }?.let { sb.append(" [timeout=$it]") }
            data.optString("natType").takeIf { it.isNotBlank() }?.let { sb.append(" [NAT=$it]") }
            data.optString("error").takeIf { it.isNotBlank() }?.let { error ->
                val trimmed = if (error.length > 120) error.take(120) + "…" else error
                sb.append(" | $trimmed")
            }
            return sb.toString().trim()
        } catch (_: Exception) {
            val idx = message.indexOf("inproxy-dial:", ignoreCase = true)
            if (idx < 0) return null
            val remainder = message.substring(idx + "inproxy-dial:".length).trim()
            val end = listOf("\",", "\"}").map { remainder.indexOf(it) }.filter { it > 0 }.minOrNull()
            return if (end != null) remainder.substring(0, end) else remainder
        }
    }

    private fun extractDuration(message: String): String? {
        val matcher = candidateDurationPattern.matcher(message)
        if (!matcher.find()) return null
        val value = matcher.group(1) ?: return null
        val unit = matcher.group(2) ?: "s"
        return if (unit.equals("ms", ignoreCase = true)) "${value}ms" else "${value}s"
    }

    private fun formatDuration(goDuration: String): String? {
        if (goDuration.isBlank()) return null
        return try {
            when {
                goDuration.endsWith("ms") -> {
                    val ms = goDuration.removeSuffix("ms").toDouble()
                    if (ms < 1) "<1ms" else "${ms.toInt()}ms"
                }
                goDuration.endsWith("s") -> {
                    val sec = goDuration.removeSuffix("s").toDouble()
                    if (sec < 10) String.format(Locale.US, "%.1fs", sec)
                    else "${sec.toInt()}s"
                }
                else -> goDuration
            }
        } catch (_: Exception) {
            goDuration
        }
    }

    private fun countryName(context: Context, code: String): String {
        if (code.isBlank()) return code
        val resId = context.resources.getIdentifier(
            "region_name_${code.lowercase(Locale.US)}",
            "string",
            context.packageName
        )
        if (resId != 0) return context.getString(resId)
        return Locale("", code).getDisplayCountry(Locale.getDefault())
    }
}
