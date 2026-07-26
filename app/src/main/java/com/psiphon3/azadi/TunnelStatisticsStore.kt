package com.psiphon3.azadi

import android.content.Context
import com.psiphon3.R
import com.psiphon3.psiphonlibrary.EmbeddedValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.grandcentrix.tray.AppPreferences
import org.json.JSONObject

/**
 * Shared tunnel statistics (iOS TunnelStatistics / App Group equivalent).
 * Written by [com.psiphon3.psiphonlibrary.TunnelManager], read by dashboard UI.
 */
object TunnelStatisticsStore {
    private const val PREFS_KEY = "tunnel_statistics_json"

    @Volatile
    private var appContext: Context? = null
    private val _flow = MutableStateFlow(TunnelStatistics.empty())
    val flow: StateFlow<TunnelStatistics> = _flow.asStateFlow()

    @JvmStatic
    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            _flow.value = load()
        }
    }

    private fun ctx(context: Context? = null): Context =
        requireNotNull(context ?: appContext) { "TunnelStatisticsStore.init() required" }

    private fun prefs(context: Context): AppPreferences = AppPreferences(context.applicationContext)

    @JvmStatic
    fun load(context: Context? = null): TunnelStatistics {
        val c = ctx(context)
        val raw = prefs(c).getString(PREFS_KEY, null) ?: return TunnelStatistics.empty()
        return try {
            TunnelStatistics.fromJson(JSONObject(raw))
        } catch (_: Exception) {
            TunnelStatistics.empty()
        }
    }

    @JvmStatic
    fun reload(context: Context? = null): TunnelStatistics {
        val stats = load(context)
        _flow.value = stats
        return stats
    }

    private fun save(context: Context, stats: TunnelStatistics) {
        prefs(context).put(PREFS_KEY, stats.toJson().toString())
        _flow.value = stats
    }

    @JvmStatic
    fun markDisconnected(context: Context) {
        init(context)
        save(context, TunnelStatistics.empty())
    }

    @JvmStatic
    fun onConnecting(context: Context, protocolSelection: String?) {
        init(context)
        var stats = load(context).copy(connectedTunnelProtocol = "")
        if (protocolSelection == "conduit") {
            val blocked = EmbeddedValues.CONDUIT_COMPARTMENT_ID.isNullOrEmpty()
            val line = if (blocked) {
                AzadiEventLogger.logSync("CONDUIT_BLOCKED")
                context.getString(R.string.azadi_conduit_blocked)
            } else {
                AzadiEventLogger.logSync("CONDUIT_STATUS", "Starting Conduit relays")
                context.getString(R.string.azadi_conduit_starting)
            }
            stats = stats.copy(
                conduitStatusLine = line,
                conduitStatusHistory = emptyList(),
                conduitStatusUpdatedAt = System.currentTimeMillis()
            )
        }
        save(context, stats)
    }

    @JvmStatic
    fun setConnectedTunnelProtocol(context: Context, raw: String) {
        if (raw.isBlank()) return
        init(context)
        val display = ConnectedTunnelProtocolParser.displayName(raw)
        AzadiEventLogger.logSync("PSIPHON_CONNECTED_PROTOCOL", "raw=$raw display=$display")
        if (raw.uppercase().startsWith("INPROXY-WEBRTC-")) {
            AzadiEventLogger.logSync("CONDUIT_CONNECTED_PROTOCOL", "raw=$raw")
        }
        save(context, load(context).copy(connectedTunnelProtocol = raw))
    }

    @JvmStatic
    fun clearConnectedTunnelProtocol(context: Context) {
        init(context)
        val stats = load(context)
        if (stats.connectedTunnelProtocol.isEmpty()) return
        save(context, stats.copy(connectedTunnelProtocol = ""))
    }

    @JvmStatic
    fun setConduitStatusLine(context: Context, rawDiagnosticMessage: String) {
        val line = ConduitStatusParser.parseDashboardLine(context, rawDiagnosticMessage) ?: return
        setConduitStatusLineParsed(context, line)
    }

    @JvmStatic
    fun setConduitStatusLineParsed(context: Context, line: String) {
        if (line.isBlank()) return
        init(context)
        val current = load(context)
        if (line == current.conduitStatusLine) return
        val history = buildList {
            if (current.conduitStatusLine.isNotBlank()) add(current.conduitStatusLine)
            addAll(current.conduitStatusHistory)
        }.take(TunnelStatistics.MAX_HISTORY_STORED)
        AzadiEventLogger.logSync("CONDUIT_STATUS", line)
        save(
            context,
            current.copy(
                conduitStatusLine = line,
                conduitStatusHistory = history,
                conduitStatusUpdatedAt = System.currentTimeMillis()
            )
        )
    }

    @JvmStatic
    fun onDiagnosticMessage(context: Context, message: String) {
        init(context)
        ConnectedTunnelProtocolParser.extractProtocol(message)?.let { raw ->
            setConnectedTunnelProtocol(context, raw)
        }
        if (ConduitStatusParser.shouldParse(message)) {
            setConduitStatusLine(context, message)
        }
        routeLegacyConduitPatterns(context, message)
    }

    /** Legacy regex paths from tunnel notices before full JSON conduit lines. */
    private fun routeLegacyConduitPatterns(context: Context, message: String) {
        if (message.contains("trying Conduit relay (country:")) {
            setConduitStatusLine(context, message)
        } else if (message.contains("tunnel connected via Conduit relay")) {
            setConduitStatusLine(context, message)
        } else if (message.contains("inproxy-dial:")) {
            setConduitStatusLine(context, message)
        } else if (message.contains("tunnel connected (protocol:")) {
            val pattern = java.util.regex.Pattern.compile("\\(protocol: ([^)]+)\\)")
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                setConnectedTunnelProtocol(context, matcher.group(1) ?: return)
            }
        }
    }

    @JvmStatic
    fun onConduitPublicFallback(context: Context) {
        init(context)
        AzadiEventLogger.logSync("CONDUIT_PUBLIC_FALLBACK")
        AzadiEventLogger.logSync("CONDUIT_FALLBACK")
        setConduitStatusLineParsed(
            context,
            context.getString(R.string.azadi_conduit_public_fallback)
        )
    }
}
