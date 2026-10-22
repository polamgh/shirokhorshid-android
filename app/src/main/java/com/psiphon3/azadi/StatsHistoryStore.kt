package com.psiphon3.azadi

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** One smoothed speed sample (bytes per second), taken once per tick. */
data class StatsSpeedSample(val timeMs: Long, val down: Long, val up: Long)

/** One latency probe result. */
data class StatsLatencySample(val timeMs: Long, val ms: Int)

/** Bytes moved on one calendar day ([day] is `yyyy-MM-dd`, Gregorian, device time zone). */
data class StatsDailyUsage(val day: String, val bytesDown: Long, val bytesUp: Long) {
    val total: Long get() = bytesDown + bytesUp
}

/** One finished (or failed) connection attempt. */
data class StatsSessionRecord(
    val id: String = UUID.randomUUID().toString(),
    val attemptStartedAt: Long,
    /** Null when the attempt never reached "connected". */
    val connectedAt: Long?,
    val endedAt: Long,
    /** Raw Psiphon protocol (e.g. `OSSH`); format with [ConnectedTunnelProtocolParser.displayName]. */
    val tunnelProtocol: String,
    /** ISO 3166 alpha-2, may be empty. */
    val countryCode: String,
    val bytesDown: Long,
    val bytesUp: Long
) {
    val succeeded: Boolean get() = connectedAt != null

    val durationMs: Long get() = connectedAt?.let { (endedAt - it).coerceAtLeast(0L) } ?: 0L

    /** Milliseconds from tapping Connect to a working tunnel. */
    val timeToConnectMs: Long? get() = connectedAt?.let { (it - attemptStartedAt).coerceAtLeast(0L) }

    internal fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("attemptStartedAt", attemptStartedAt)
        if (connectedAt != null) put("connectedAt", connectedAt)
        put("endedAt", endedAt)
        put("tunnelProtocol", tunnelProtocol)
        put("countryCode", countryCode)
        put("bytesDown", bytesDown)
        put("bytesUp", bytesUp)
    }

    internal companion object {
        fun fromJson(obj: JSONObject): StatsSessionRecord = StatsSessionRecord(
            id = obj.optString("id").ifEmpty { UUID.randomUUID().toString() },
            attemptStartedAt = obj.optLong("attemptStartedAt", 0L),
            connectedAt = if (obj.has("connectedAt")) obj.optLong("connectedAt") else null,
            endedAt = obj.optLong("endedAt", 0L),
            tunnelProtocol = obj.optString("tunnelProtocol", ""),
            countryCode = obj.optString("countryCode", ""),
            bytesDown = obj.optLong("bytesDown", 0L),
            bytesUp = obj.optLong("bytesUp", 0L)
        )
    }
}

enum class StatsLinkState { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

data class StatsProtocolShare(val name: String, val count: Int)

/** Immutable view of everything the Stats tab draws. Published once per tick. */
data class StatsSnapshot(
    /** Wall-clock time of the tick that produced this snapshot. */
    val nowMs: Long = System.currentTimeMillis(),
    val linkState: StatsLinkState = StatsLinkState.DISCONNECTED,
    val speedSamples: List<StatsSpeedSample> = emptyList(),
    val latencySamples: List<StatsLatencySample> = emptyList(),
    val dailyUsage: List<StatsDailyUsage> = emptyList(),
    /** Newest first, at most 100. */
    val sessions: List<StatsSessionRecord> = emptyList(),
    val sessionPeakDown: Long = 0L,
    val sessionPeakUp: Long = 0L,
    /** Y-axis ceiling for the live speed chart (bytes/s); eased, follows the last ~20 samples. */
    val speedChartScale: Double = StatsHistoryStore.INITIAL_CHART_SCALE,
    /** Wall-clock connect time of the open session, null when not connected. */
    val currentConnectedAt: Long? = null,
    val currentSessionDown: Long = 0L,
    val currentSessionUp: Long = 0L,
    /** Raw protocol of the open session. */
    val currentProtocol: String = "",
    val allTimeDown: Long = 0L,
    val allTimeUp: Long = 0L
) {
    val isConnected: Boolean get() = linkState == StatsLinkState.CONNECTED

    val latestSpeed: StatsSpeedSample? get() = speedSamples.lastOrNull()

    val currentSessionDurationMs: Long
        get() = if (isConnected && currentConnectedAt != null) (nowMs - currentConnectedAt).coerceAtLeast(0L) else 0L

    val successRate: Double?
        get() = if (sessions.isEmpty()) null else sessions.count { it.succeeded }.toDouble() / sessions.size

    val averageTimeToConnectMs: Long?
        get() {
            val values = sessions.mapNotNull { it.timeToConnectMs }
            return if (values.isEmpty()) null else values.sum() / values.size
        }

    val totalProtectedTimeMs: Long
        get() = sessions.sumOf { it.durationMs } + currentSessionDurationMs

    /** Successful sessions per protocol display name, most used first. */
    val protocolBreakdown: List<StatsProtocolShare>
        get() {
            val counts = LinkedHashMap<String, Int>()
            for (session in sessions) {
                if (!session.succeeded) continue
                val name = ConnectedTunnelProtocolParser.displayName(session.tunnelProtocol).ifBlank { "—" }
                counts[name] = (counts[name] ?: 0) + 1
            }
            return counts.map { StatsProtocolShare(it.key, it.value) }.sortedByDescending { it.count }
        }

    val averageLatency: Int?
        get() = if (latencySamples.isEmpty()) null else latencySamples.sumOf { it.ms } / latencySamples.size

    /** The last 7 days ending today (oldest first), zero-filled. */
    fun lastSevenDays(): List<StatsDailyUsage> {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowMs
        val byDay = dailyUsage.associateBy { it.day }
        val result = ArrayList<StatsDailyUsage>(7)
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        repeat(7) {
            val key = StatsHistoryStore.dayKey(calendar.timeInMillis)
            result.add(byDay[key] ?: StatsDailyUsage(key, 0L, 0L))
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }
}

/**
 * Records live speed, latency, per-day usage and connection sessions for the Stats tab
 * (port of iOS `StatsHistoryStore`). The activity drives it: [tick] once per second plus the
 * connection lifecycle calls. History (daily usage, sessions, all-time totals, the open
 * session) is persisted in SharedPreferences; live samples are in memory only.
 */
object StatsHistoryStore {
    internal const val INITIAL_CHART_SCALE = 50_000.0

    private const val PREFS_NAME = "azadi_stats_history"
    private const val KEY_DAILY = "stats_daily_usage_json"
    private const val KEY_SESSIONS = "stats_sessions_json"
    private const val KEY_OPEN_SESSION = "stats_open_session_json"
    private const val KEY_ALL_TIME_DOWN = "stats_all_time_down"
    private const val KEY_ALL_TIME_UP = "stats_all_time_up"

    private const val MAX_SPEED_SAMPLES = 120
    private const val MAX_LATENCY_SAMPLES = 60
    private const val MAX_SESSIONS = 100
    private const val MAX_DAYS = 30
    /** Seconds of byte deltas averaged into one speed sample (Psiphon reports in bursts). */
    private const val SPEED_WINDOW = 3
    /** Samples considered when picking the live chart's ceiling. */
    private const val SCALE_WINDOW = 20
    private const val MIN_CHART_SCALE = 4_000.0
    /** Seconds between latency probes while connected. */
    private const val LATENCY_INTERVAL = 3
    private const val PERSIST_EVERY_TICKS = 15
    /** An open session left by a previous process is reused when its start is this close. */
    private const val ADOPT_TOLERANCE_MS = 60_000L
    private const val MAX_CONNECTED_FOR_MS = 7L * 24 * 3600 * 1000

    private val _flow = MutableStateFlow(StatsSnapshot())
    val flow: StateFlow<StatsSnapshot> = _flow.asStateFlow()

    private val lock = Any()
    private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var prefs: SharedPreferences? = null
    private var autoProbeLatency = true
    private var demoMode = false

    private val speedSamples = ArrayList<StatsSpeedSample>()
    private val latencySamples = ArrayList<StatsLatencySample>()
    private val dailyUsage = ArrayList<StatsDailyUsage>()
    private val sessions = ArrayList<StatsSessionRecord>()
    private val recentDeltas = ArrayList<LongArray>()

    private var linkState = StatsLinkState.DISCONNECTED
    private var speedChartScale = INITIAL_CHART_SCALE
    private var sessionPeakDown = 0L
    private var sessionPeakUp = 0L
    private var allTimeDown = 0L
    private var allTimeUp = 0L

    private var speedBaselineDown: Long? = null
    private var speedBaselineUp: Long? = null
    private var usageBaselineDown: Long? = null
    private var usageBaselineUp: Long? = null

    private var attemptStartedAt: Long? = null
    private var openSession: StatsSessionRecord? = null
    /** Open session persisted by an earlier process, not yet adopted or closed. */
    private var orphanSession: StatsSessionRecord? = null

    private var latencyTicks = 0
    private var latencyInFlight = false
    private var ticksSincePersist = 0
    private var lastTickAt = System.currentTimeMillis()

    // region Public API

    /**
     * Loads persisted history. Safe to call repeatedly. With [autoProbeLatency] the store pings
     * every 3 s while connected (via [PostConnectDiagnostics.measurePingMs]); pass false to feed
     * latency yourself through [recordLatency].
     */
    @JvmStatic
    @JvmOverloads
    fun init(context: Context, autoProbeLatency: Boolean = true) {
        synchronized(lock) {
            this.autoProbeLatency = autoProbeLatency
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = p
            dailyUsage.clear()
            dailyUsage.addAll(loadDaily(p))
            sessions.clear()
            sessions.addAll(loadSessions(p))
            orphanSession = p.getString(KEY_OPEN_SESSION, null)?.let {
                runCatching { StatsSessionRecord.fromJson(JSONObject(it)) }.getOrNull()
            }
            allTimeDown = p.getLong(KEY_ALL_TIME_DOWN, 0L)
            allTimeUp = p.getLong(KEY_ALL_TIME_UP, 0L)
            // Seed all-time from the dashboard's lifetime counters so both screens agree.
            runCatching {
                val (down, up) = PersistentTrafficStatsStore(context.applicationContext).load()
                allTimeDown = max(allTimeDown, down)
                allTimeUp = max(allTimeUp, up)
            }
            publish(System.currentTimeMillis())
        }
    }

    /**
     * Call once per second (whatever the connection state).
     *
     * @param isConnected tunnel is up.
     * @param downloadBytesPerSec / [uploadBytesPerSec] the service's instantaneous speed; only
     *   used as a fallback when the cumulative counters give no usable delta.
     * @param sessionDown / [sessionUp] cumulative byte counters
     *   (`DataTransferStats.getDataTransferStatsForUI().totalBytesReceived / totalBytesSent`);
     *   speed, daily usage and per-session bytes come from their deltas, and a decrease is
     *   treated as a counter reset and skipped.
     * @param tunnelProtocol raw protocol (e.g. `tunnelStats.connectedTunnelProtocol`); fills in
     *   the open session's protocol once known.
     * @param lifetimeDown / [lifetimeUp] the dashboard's persisted lifetime totals
     *   ([PersistentTrafficStatsStore]); pass them so "All time" matches the dashboard, or -1 to
     *   let the store accumulate its own.
     */
    @JvmStatic
    @JvmOverloads
    fun tick(
        isConnected: Boolean,
        downloadBytesPerSec: Long,
        uploadBytesPerSec: Long,
        sessionDown: Long,
        sessionUp: Long,
        tunnelProtocol: String = "",
        lifetimeDown: Long = -1L,
        lifetimeUp: Long = -1L
    ) {
        var launchProbe = false
        synchronized(lock) {
            if (demoMode) return
            val now = System.currentTimeMillis()
            lastTickAt = now

            if (isConnected && openSession == null) {
                // Status reached us before onConnected(); open the session now.
                openSessionLocked(now, connectedForMs = 0L, tunnelProtocol = tunnelProtocol, countryCode = "")
            }
            if (isConnected) linkState = StatsLinkState.CONNECTED
            openSession?.let { session ->
                if (session.tunnelProtocol.isEmpty() && tunnelProtocol.isNotBlank()) {
                    openSession = session.copy(tunnelProtocol = tunnelProtocol)
                }
            }

            // Live speed (3 s smoothed).
            val speed = smoothedSpeed(isConnected, sessionDown, sessionUp, downloadBytesPerSec, uploadBytesPerSec)
            speedSamples.add(StatsSpeedSample(now, speed[0], speed[1]))
            trim(speedSamples, MAX_SPEED_SAMPLES)
            updateSpeedChartScale()
            if (isConnected) {
                sessionPeakDown = max(sessionPeakDown, speed[0])
                sessionPeakUp = max(sessionPeakUp, speed[1])
            }

            // Byte deltas -> daily usage, open session, all-time.
            val prevDown = usageBaselineDown
            val prevUp = usageBaselineUp
            usageBaselineDown = sessionDown
            usageBaselineUp = sessionUp
            if (prevDown != null && prevUp != null) {
                val down = if (sessionDown >= prevDown) sessionDown - prevDown else 0L
                val up = if (sessionUp >= prevUp) sessionUp - prevUp else 0L
                if (down > 0L || up > 0L) {
                    accumulateDaily(now, down, up)
                    openSession?.let {
                        openSession = it.copy(bytesDown = it.bytesDown + down, bytesUp = it.bytesUp + up)
                    }
                    if (lifetimeDown < 0L) allTimeDown += down
                    if (lifetimeUp < 0L) allTimeUp += up
                }
            }
            if (lifetimeDown >= 0L) allTimeDown = max(allTimeDown, lifetimeDown)
            if (lifetimeUp >= 0L) allTimeUp = max(allTimeUp, lifetimeUp)

            // Latency probe every LATENCY_INTERVAL ticks while connected.
            latencyTicks++
            if (isConnected && autoProbeLatency && latencyTicks >= LATENCY_INTERVAL && !latencyInFlight) {
                latencyTicks = 0
                latencyInFlight = true
                launchProbe = true
            }

            ticksSincePersist++
            if (ticksSincePersist >= PERSIST_EVERY_TICKS) {
                ticksSincePersist = 0
                persistDaily()
                persistTotals()
                persistOpenSession()
            }
            publish(now)
        }
        if (launchProbe) {
            probeScope.launch {
                val ms = runCatching { PostConnectDiagnostics.measurePingMs() }.getOrDefault(-1L)
                synchronized(lock) {
                    latencyInFlight = false
                    appendLatencyLocked(ms)
                    publish(System.currentTimeMillis())
                }
            }
        }
    }

    /** Adds a latency measurement (ms). Ignored when negative or not connected. */
    @JvmStatic
    fun recordLatency(ms: Long) {
        synchronized(lock) {
            if (demoMode) return
            appendLatencyLocked(ms)
            publish(System.currentTimeMillis())
        }
    }

    /** The user tapped Connect (or auto-connect started). */
    @JvmStatic
    fun onConnectAttempt() {
        synchronized(lock) {
            if (demoMode) return
            val now = System.currentTimeMillis()
            closeOrphanLocked()
            if (attemptStartedAt == null && openSession == null) attemptStartedAt = now
            if (openSession == null) linkState = StatsLinkState.CONNECTING
            publish(now)
        }
    }

    /**
     * The tunnel is connected.
     *
     * @param tunnelProtocol raw protocol if already known (may be empty; [tick] fills it later).
     * @param connectedForMs how long the tunnel has already been up
     *   (`DataTransferStats.getDataTransferStatsForUI().elapsedTime`), so a session that was
     *   running before the app (re)started gets its real start time. 0 if unknown.
     * @param countryCode ISO country of the exit, if known (e.g. the chosen egress region).
     */
    @JvmStatic
    @JvmOverloads
    fun onConnected(tunnelProtocol: String = "", connectedForMs: Long = 0L, countryCode: String = "") {
        synchronized(lock) {
            if (demoMode) return
            val now = System.currentTimeMillis()
            val existing = openSession
            if (existing == null) {
                openSessionLocked(now, connectedForMs, tunnelProtocol, countryCode)
            } else {
                var session = existing
                val connectedFor = sanitizeConnectedFor(connectedForMs)
                if (connectedFor > 0L) {
                    val realStart = now - connectedFor
                    val currentStart = session.connectedAt ?: now
                    if (realStart < currentStart) {
                        session = session.copy(
                            connectedAt = realStart,
                            attemptStartedAt = minOf(session.attemptStartedAt, realStart)
                        )
                    }
                }
                if (session.tunnelProtocol.isEmpty() && tunnelProtocol.isNotBlank()) {
                    session = session.copy(tunnelProtocol = tunnelProtocol)
                }
                if (session.countryCode.isEmpty() && countryCode.isNotBlank()) {
                    session = session.copy(countryCode = countryCode.uppercase(Locale.US))
                }
                openSession = session
            }
            linkState = StatsLinkState.CONNECTED
            persistOpenSession()
            publish(now)
        }
    }

    /** Sets the open session's exit country once a geo lookup knows it. */
    @JvmStatic
    fun setSessionCountry(countryCode: String) {
        synchronized(lock) {
            val code = countryCode.trim().uppercase(Locale.US)
            if (code.length != 2) return
            val session = openSession ?: return
            if (session.countryCode == code) return
            openSession = session.copy(countryCode = code)
            persistOpenSession()
            publish(System.currentTimeMillis())
        }
    }

    /** The user stopped a connection attempt before it connected: do not count it as failed. */
    @JvmStatic
    fun onConnectCancelled() {
        synchronized(lock) {
            if (demoMode) return
            attemptStartedAt = null
            if (openSession == null) linkState = StatsLinkState.DISCONNECTED
            publish(System.currentTimeMillis())
        }
    }

    /**
     * The tunnel is down (disconnected or errored). Closes and records the open session, or
     * records a failed attempt if one was pending.
     */
    @JvmStatic
    @JvmOverloads
    fun onDisconnected(failed: Boolean = false) {
        synchronized(lock) {
            if (demoMode) return
            val now = System.currentTimeMillis()
            val session = openSession
            when {
                session != null -> {
                    recordLocked(session.copy(endedAt = now))
                    openSession = null
                    attemptStartedAt = null
                    linkState = if (failed) StatsLinkState.FAILED else StatsLinkState.DISCONNECTED
                }
                attemptStartedAt != null -> {
                    recordLocked(
                        StatsSessionRecord(
                            attemptStartedAt = attemptStartedAt!!,
                            connectedAt = null,
                            endedAt = now,
                            tunnelProtocol = "",
                            countryCode = "",
                            bytesDown = 0L,
                            bytesUp = 0L
                        )
                    )
                    attemptStartedAt = null
                    linkState = StatsLinkState.FAILED
                }
                else -> {
                    closeOrphanLocked()
                    linkState = if (failed) StatsLinkState.FAILED else StatsLinkState.DISCONNECTED
                }
            }
            recentDeltas.clear()
            persistOpenSession()
            persistDaily()
            persistTotals()
            publish(now)
        }
    }

    /** Debug only: fills every chart with plausible data (iOS `-UITestStatsDemo`). Ticks are ignored afterwards. */
    @JvmStatic
    fun loadDemoData() {
        synchronized(lock) {
            demoMode = true
            val now = System.currentTimeMillis()
            val random = Random(now)
            speedSamples.clear()
            for (index in 0 until MAX_SPEED_SAMPLES) {
                val t = index.toDouble()
                val down = 900_000 + 650_000 * sin(t / 9) + 300_000 * sin(t / 3.1) + random.nextDouble(0.0, 180_000.0)
                val up = 120_000 + 70_000 * sin(t / 7 + 1) + random.nextDouble(0.0, 40_000.0)
                speedSamples.add(
                    StatsSpeedSample(
                        timeMs = now + ((index - MAX_SPEED_SAMPLES) * 1000L),
                        down = max(0.0, down).toLong(),
                        up = max(0.0, up).toLong()
                    )
                )
            }
            latencySamples.clear()
            for (index in 0 until 30) {
                latencySamples.add(
                    StatsLatencySample(
                        timeMs = now + (index * 5 - 150) * 1000L,
                        ms = 70 + (25 * sin(index / 3.0)).toInt() + random.nextInt(0, 21)
                    )
                )
            }
            dailyUsage.clear()
            val calendar = Calendar.getInstance()
            for (offset in 0 until 7) {
                calendar.timeInMillis = now
                calendar.add(Calendar.DAY_OF_YEAR, -offset)
                dailyUsage.add(
                    StatsDailyUsage(
                        day = dayKey(calendar.timeInMillis),
                        bytesDown = random.nextLong(80_000_000L, 900_000_000L),
                        bytesUp = random.nextLong(8_000_000L, 90_000_000L)
                    )
                )
            }
            dailyUsage.sortBy { it.day }
            val protocols = listOf("OSSH", "OSSH", "QUIC-OSSH", "FRONTED-MEEK-CDN-OSSH", "SSH", "TLS-OSSH", "OSSH")
            val countries = listOf("DE", "NL", "GB", "FR", "US", "DE", "CA")
            sessions.clear()
            for (index in 0 until 9) {
                val start = now - index * 9_000_000L - 600_000L
                val succeeded = index != 3 && index != 7
                sessions.add(
                    StatsSessionRecord(
                        attemptStartedAt = start,
                        connectedAt = if (succeeded) start + random.nextLong(8_000L, 40_000L) else null,
                        endedAt = start + random.nextLong(900_000L, 5_400_000L),
                        tunnelProtocol = if (succeeded) protocols[index % protocols.size] else "",
                        countryCode = if (succeeded) countries[index % countries.size] else "",
                        bytesDown = if (succeeded) random.nextLong(20_000_000L, 600_000_000L) else 0L,
                        bytesUp = if (succeeded) random.nextLong(2_000_000L, 60_000_000L) else 0L
                    )
                )
            }
            sessionPeakDown = speedSamples.maxOfOrNull { it.down } ?: 0L
            sessionPeakUp = speedSamples.maxOfOrNull { it.up } ?: 0L
            updateSpeedChartScale()
            allTimeDown = 18_400_000_000L
            allTimeUp = 1_900_000_000L
            openSession = StatsSessionRecord(
                attemptStartedAt = now - 2_780_000L,
                connectedAt = now - 2_754_000L,
                endedAt = now,
                tunnelProtocol = "OSSH",
                countryCode = "DE",
                bytesDown = 412_000_000L,
                bytesUp = 38_000_000L
            )
            linkState = StatsLinkState.CONNECTED
            publish(now)
        }
    }

    // endregion

    // region Internals (call with lock held)

    private fun openSessionLocked(now: Long, connectedForMs: Long, tunnelProtocol: String, countryCode: String) {
        val connectedFor = sanitizeConnectedFor(connectedForMs)
        val connectedAt = if (connectedFor > 0L) now - connectedFor else now
        val orphan = orphanSession
        orphanSession = null
        if (orphan != null && attemptStartedAt == null) {
            // App restarted while connected: continue the session from the previous process
            // if it is plausibly the same tunnel.
            val orphanStart = orphan.connectedAt ?: orphan.attemptStartedAt
            if (connectedFor == 0L || kotlin.math.abs(orphanStart - connectedAt) <= ADOPT_TOLERANCE_MS) {
                openSession = orphan.copy(
                    endedAt = now,
                    tunnelProtocol = orphan.tunnelProtocol.ifEmpty { tunnelProtocol },
                    countryCode = orphan.countryCode.ifEmpty { countryCode.uppercase(Locale.US) }
                )
                return
            }
        }
        if (orphan != null) recordLocked(orphan)
        openSession = StatsSessionRecord(
            attemptStartedAt = attemptStartedAt?.let { minOf(it, connectedAt) } ?: connectedAt,
            connectedAt = connectedAt,
            endedAt = now,
            tunnelProtocol = tunnelProtocol,
            countryCode = countryCode.trim().uppercase(Locale.US),
            bytesDown = 0L,
            bytesUp = 0L
        )
        attemptStartedAt = null
        sessionPeakDown = 0L
        sessionPeakUp = 0L
    }

    /**
     * DataTransferStats.elapsedTime is `elapsedRealtime - connectedTime`, which equals the
     * device uptime while connectedTime is still 0; treat that (and nonsense values) as unknown.
     */
    private fun sanitizeConnectedFor(connectedForMs: Long): Long {
        if (connectedForMs <= 0L) return 0L
        if (connectedForMs >= SystemClock.elapsedRealtime() - 1_000L) return 0L
        return connectedForMs.coerceAtMost(MAX_CONNECTED_FOR_MS)
    }

    /** Records a session left open by a previous process, ending at its last-seen time. */
    private fun closeOrphanLocked() {
        val orphan = orphanSession ?: return
        orphanSession = null
        recordLocked(orphan)
        persistOpenSession()
    }

    private fun smoothedSpeed(
        connected: Boolean,
        totalDown: Long,
        totalUp: Long,
        reportedDown: Long,
        reportedUp: Long
    ): LongArray {
        val baseDown = speedBaselineDown
        val baseUp = speedBaselineUp
        speedBaselineDown = totalDown
        speedBaselineUp = totalUp
        if (!connected) {
            recentDeltas.clear()
            return longArrayOf(0L, 0L)
        }
        val down = if (baseDown != null && totalDown >= baseDown) totalDown - baseDown else reportedDown.coerceAtLeast(0L)
        val up = if (baseUp != null && totalUp >= baseUp) totalUp - baseUp else reportedUp.coerceAtLeast(0L)
        recentDeltas.add(longArrayOf(down, up))
        while (recentDeltas.size > SPEED_WINDOW) recentDeltas.removeAt(0)
        val count = recentDeltas.size.toLong()
        return longArrayOf(recentDeltas.sumOf { it[0] } / count, recentDeltas.sumOf { it[1] } / count)
    }

    /** Jumps up to new peaks, eases down as speed falls so the curve keeps filling the chart. */
    private fun updateSpeedChartScale() {
        val recentPeak = speedSamples.takeLast(SCALE_WINDOW).maxOfOrNull { max(it.down, it.up).toDouble() } ?: 0.0
        val target = max(recentPeak * 1.25, MIN_CHART_SCALE)
        speedChartScale = if (target > speedChartScale) target else speedChartScale * 0.8 + target * 0.2
    }

    private fun appendLatencyLocked(ms: Long) {
        if (ms < 0L || linkState != StatsLinkState.CONNECTED) return
        latencySamples.add(StatsLatencySample(System.currentTimeMillis(), ms.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
        trim(latencySamples, MAX_LATENCY_SAMPLES)
    }

    private fun accumulateDaily(now: Long, down: Long, up: Long) {
        val key = dayKey(now)
        val index = dailyUsage.indexOfFirst { it.day == key }
        if (index >= 0) {
            val day = dailyUsage[index]
            dailyUsage[index] = day.copy(bytesDown = day.bytesDown + down, bytesUp = day.bytesUp + up)
        } else {
            dailyUsage.add(StatsDailyUsage(key, down, up))
            dailyUsage.sortBy { it.day }
            while (dailyUsage.size > MAX_DAYS) dailyUsage.removeAt(0)
        }
    }

    private fun recordLocked(session: StatsSessionRecord) {
        sessions.add(0, session)
        while (sessions.size > MAX_SESSIONS) sessions.removeAt(sessions.size - 1)
        persistSessions()
        persistDaily()
    }

    private fun <T> trim(list: MutableList<T>, maxSize: Int) {
        if (list.size > maxSize) list.subList(0, list.size - maxSize).clear()
    }

    private fun publish(now: Long) {
        val session = openSession
        _flow.value = StatsSnapshot(
            nowMs = now,
            linkState = linkState,
            speedSamples = speedSamples.toList(),
            latencySamples = latencySamples.toList(),
            dailyUsage = dailyUsage.toList(),
            sessions = sessions.toList(),
            sessionPeakDown = sessionPeakDown,
            sessionPeakUp = sessionPeakUp,
            speedChartScale = speedChartScale,
            currentConnectedAt = session?.connectedAt,
            currentSessionDown = session?.bytesDown ?: 0L,
            currentSessionUp = session?.bytesUp ?: 0L,
            currentProtocol = session?.tunnelProtocol ?: "",
            allTimeDown = allTimeDown,
            allTimeUp = allTimeUp
        )
    }

    // endregion

    // region Persistence

    private fun persistDaily() {
        if (demoMode) return
        val p = prefs ?: return
        val arr = JSONArray()
        dailyUsage.forEach {
            arr.put(JSONObject().put("day", it.day).put("bytesDown", it.bytesDown).put("bytesUp", it.bytesUp))
        }
        p.edit().putString(KEY_DAILY, arr.toString()).apply()
    }

    private fun persistSessions() {
        if (demoMode) return
        val p = prefs ?: return
        val arr = JSONArray()
        sessions.forEach { arr.put(it.toJson()) }
        p.edit().putString(KEY_SESSIONS, arr.toString()).apply()
    }

    private fun persistTotals() {
        if (demoMode) return
        val p = prefs ?: return
        p.edit().putLong(KEY_ALL_TIME_DOWN, allTimeDown).putLong(KEY_ALL_TIME_UP, allTimeUp).apply()
    }

    /** Saves the open session (with endedAt = last seen) so a process death does not lose it. */
    private fun persistOpenSession() {
        if (demoMode) return
        val p = prefs ?: return
        val session = openSession
        if (session == null) {
            if (orphanSession == null) p.edit().remove(KEY_OPEN_SESSION).apply()
            return
        }
        p.edit().putString(KEY_OPEN_SESSION, session.copy(endedAt = lastTickAt).toJson().toString()).apply()
    }

    private fun loadDaily(p: SharedPreferences): List<StatsDailyUsage> {
        val raw = p.getString(KEY_DAILY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = ArrayList<StatsDailyUsage>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val day = obj.optString("day")
                if (day.isNotEmpty()) result.add(StatsDailyUsage(day, obj.optLong("bytesDown"), obj.optLong("bytesUp")))
            }
            result.sortedBy { it.day }.takeLast(MAX_DAYS)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadSessions(p: SharedPreferences): List<StatsSessionRecord> {
        val raw = p.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = ArrayList<StatsSessionRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                result.add(StatsSessionRecord.fromJson(obj))
            }
            result.take(MAX_SESSIONS)
        } catch (_: Exception) {
            emptyList()
        }
    }

    // endregion

    /** `yyyy-MM-dd` (Gregorian, device time zone) for [timeMs]. */
    @JvmStatic
    fun dayKey(timeMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timeMs))

    /** Parses a [dayKey] back to a timestamp (local midnight), or null. */
    @JvmStatic
    fun dateForDay(day: String): Long? =
        runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day)?.time }.getOrNull()
}
