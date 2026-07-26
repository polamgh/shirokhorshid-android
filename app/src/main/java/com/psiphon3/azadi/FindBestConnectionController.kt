package com.psiphon3.azadi

import com.psiphon3.TunnelState
import com.psiphon3.psiphonlibrary.PsiphonConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.grandcentrix.tray.AppPreferences
import org.json.JSONArray
import org.json.JSONObject

data class BestConnectionCandidate(
    val protocol: String,
    val region: String,
    val speedMbps: Double
)

data class FindBestState(
    val running: Boolean = false,
    val progress: String? = null,
    val results: List<BestConnectionCandidate> = emptyList(),
    val savedBest: BestConnectionCandidate? = null,
    val askContinue: Boolean = false
)

class FindBestConnectionController(
    private val settingsStore: AzadiSettingsStore,
    private val prefs: AppPreferences,
    private val protocolKey: String,
    private val beastKey: String,
    private val regionKey: String
) {
    private val _state = MutableStateFlow(FindBestState())
    val state: StateFlow<FindBestState> = _state.asStateFlow()

    private var cancelled = false

    private val regions = listOf(
        PsiphonConstants.REGION_CODE_ANY, "US", "CA", "GB", "DE", "FR", "NL",
        "CH", "SE", "JP", "SG", "AU", "IR", "AE", "IN", "BR", "ZA"
    )

    fun cancel() {
        cancelled = true
        AzadiEventLogger.logSync("BEST_CONN_CANCELLED")
    }

    suspend fun runScan(
        minSpeedMbps: Int,
        conduitAllowed: Boolean,
        startTunnel: suspend () -> Boolean,
        stopTunnel: () -> Unit,
        awaitTunnelState: suspend () -> TunnelState,
        measureSpeedMbps: suspend () -> Double
    ): BestConnectionCandidate? {
        cancelled = false
        val original = settingsStore.load()
        val working = mutableListOf<BestConnectionCandidate>()
        AzadiEventLogger.logSync("BEST_CONN_STARTED", "minMbps=$minSpeedMbps")

        _state.value = FindBestState(running = true, progress = "Starting scan…")

        try {
            val phases = buildList {
                add("direct" to regions)
                add("cdn_fronting" to regions)
                if (conduitAllowed) add("conduit" to listOf(PsiphonConstants.REGION_CODE_ANY))
            }

            for ((protocol, regionList) in phases) {
                if (cancelled) break
                for (region in regionList) {
                    if (cancelled) break
                    val label = "$protocol @ $region"
                    _state.value = _state.value.copy(progress = label)
                    AzadiEventLogger.logSync("BEST_CONN_CANDIDATE_STARTED", label)

                    applyTrial(original, protocol, region, beast = protocol != "direct")
                    stopTunnel()
                    delay(800)
                    if (!startTunnel()) {
                        AzadiEventLogger.logSync("BEST_CONN_CANDIDATE_FAILED", "$label vpn_denied")
                        continue
                    }

                    val connected = waitConnected(awaitTunnelState, 28_000)
                    if (!connected) {
                        AzadiEventLogger.logSync("BEST_CONN_CANDIDATE_FAILED", label)
                        continue
                    }

                    val speed = measureSpeedMbps()
                    if (speed >= minSpeedMbps) {
                        val candidate = BestConnectionCandidate(protocol, region, speed)
                        working.add(candidate)
                        AzadiEventLogger.logSync("BEST_CONN_CANDIDATE_SUCCESS", "$label ${speed}Mbps")
                        _state.value = _state.value.copy(results = working.toList())
                        if (working.size >= 5) {
                            _state.value = _state.value.copy(askContinue = true)
                            delay(500)
                            if (cancelled) break
                        }
                    } else {
                        AzadiEventLogger.logSync("BEST_CONN_CANDIDATE_FAILED", "$label speed=$speed")
                    }
                    stopTunnel()
                    delay(500)
                }
            }

            restore(original)
            val best = working.maxByOrNull { it.speedMbps }
            if (best != null) {
                saveBest(best)
                AzadiEventLogger.logSync("BEST_CONN_SAVED", "${best.protocol}@${best.region} ${best.speedMbps}Mbps")
            }
            _state.value = _state.value.copy(
                running = false,
                progress = null,
                savedBest = best,
                askContinue = false
            )
            return best
        } catch (e: CancellationException) {
            restore(original)
            throw e
        } catch (e: Exception) {
            restore(original)
            _state.value = _state.value.copy(running = false, progress = null)
            return null
        }
    }

    fun applyBest(): AzadiSettings {
        val best = _state.value.savedBest ?: return settingsStore.load()
        val updated = settingsStore.load().copy(
            protocolSelection = best.protocol,
            egressRegion = best.region,
            beastModeEnabled = best.protocol != "direct"
        )
        settingsStore.save(updated)
        prefs.put(protocolKey, best.protocol)
        prefs.put(beastKey, updated.beastModeEnabled)
        prefs.put(regionKey, best.region)
        return updated
    }

    private fun applyTrial(base: AzadiSettings, protocol: String, region: String, beast: Boolean) {
        val trial = base.copy(protocolSelection = protocol, egressRegion = region, beastModeEnabled = beast)
        settingsStore.save(trial)
        prefs.put(protocolKey, protocol)
        prefs.put(beastKey, beast)
        prefs.put(regionKey, region)
    }

    private fun restore(original: AzadiSettings) {
        settingsStore.save(original)
        prefs.put(protocolKey, original.protocolSelection)
        prefs.put(beastKey, original.beastModeEnabled)
        prefs.put(regionKey, original.egressRegion)
    }

    private suspend fun waitConnected(awaitTunnelState: suspend () -> TunnelState, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && !cancelled) {
            val state = awaitTunnelState()
            if (state.isRunning && state.connectionData()?.isConnected == true) return true
            delay(1000)
        }
        return false
    }

    private fun saveBest(best: BestConnectionCandidate) {
        val json = JSONObject()
            .put("protocol", best.protocol)
            .put("region", best.region)
            .put("speedMbps", best.speedMbps)
        prefs.put("azadi_best_connection", json.toString())
    }

    fun loadSavedBest(): BestConnectionCandidate? {
        val raw = prefs.getString("azadi_best_connection", null) ?: return null
        return try {
            val json = JSONObject(raw)
            BestConnectionCandidate(
                protocol = json.getString("protocol"),
                region = json.getString("region"),
                speedMbps = json.getDouble("speedMbps")
            )
        } catch (_: Exception) {
            null
        }
    }
}
