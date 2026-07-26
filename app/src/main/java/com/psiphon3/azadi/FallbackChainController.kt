package com.psiphon3.azadi

import com.psiphon3.TunnelState
import com.psiphon3.psiphonlibrary.TunnelServiceInteractor
import kotlinx.coroutines.delay
import net.grandcentrix.tray.AppPreferences

data class FallbackStep(
    val protocolSelection: String,
    val beastMode: Boolean,
    val timeoutSeconds: Long
)

object FallbackChainController {
    private val escapeHatches = listOf(
        FallbackStep("cdn_fronting", beastMode = true, timeoutSeconds = 120),
        FallbackStep("auto", beastMode = true, timeoutSeconds = 120),
        FallbackStep("direct", beastMode = false, timeoutSeconds = 120)
    )

    fun stepsFor(protocolSelection: String): List<FallbackStep> {
        val cdn = FallbackStep("cdn_fronting", beastMode = true, timeoutSeconds = 120)
        val auto = FallbackStep("auto", beastMode = true, timeoutSeconds = 120)
        val direct = FallbackStep("direct", beastMode = false, timeoutSeconds = 120)
        return when (protocolSelection) {
            "cdn_fronting" -> listOf(cdn, auto, direct)
            "auto" -> listOf(cdn, direct)
            "direct" -> listOf(direct)
            else -> emptyList()
        }
    }

    /**
     * Ordered connection attempts: user selection first, then smart chain, then escape routes.
     * Used after tunnel connect to verify real internet and retry with another transport if needed.
     */
    fun internetAwareAttemptsFor(settings: AzadiSettings): List<FallbackStep> {
        val seen = LinkedHashSet<Pair<String, Boolean>>()
        val out = ArrayList<FallbackStep>()

        fun add(step: FallbackStep) {
            val key = step.protocolSelection to step.beastMode
            if (seen.add(key)) out.add(step)
        }

        add(FallbackStep(settings.protocolSelection, settings.beastModeEnabled, 120))

        if (settings.smartFallbackChainEnabled && settings.protocolSelection != "conduit") {
            stepsFor(settings.protocolSelection).forEach(::add)
        }

        escapeHatches.forEach(::add)
        return out
    }

    fun shouldUseChain(settings: AzadiSettings): Boolean {
        if (!settings.smartFallbackChainEnabled) return false
        if (settings.protocolSelection == "conduit") return false
        return stepsFor(settings.protocolSelection).isNotEmpty()
    }

    suspend fun connectWithChain(
        settingsStore: AzadiSettingsStore,
        interactor: TunnelServiceInteractor,
        prefs: AppPreferences,
        protocolKey: String,
        beastKey: String,
        startTunnel: () -> Unit,
        stopTunnel: () -> Unit,
        awaitTunnelState: suspend () -> TunnelState
    ): Boolean {
        val original = settingsStore.load()
        val chain = stepsFor(original.protocolSelection)
        for (step in chain) {
            val trial = original.copy(
                protocolSelection = step.protocolSelection,
                beastModeEnabled = step.beastMode
            )
            settingsStore.save(trial)
            prefs.put(protocolKey, step.protocolSelection)
            prefs.put(beastKey, step.beastMode)

            val stateBeforeStart = awaitTunnelState()
            if (stateBeforeStart.isRunning) {
                stopTunnel()
                delay(1000)
            }
            startTunnel()

            val deadline = System.currentTimeMillis() + step.timeoutSeconds * 1000
            var connected = false
            while (System.currentTimeMillis() < deadline) {
                val state = awaitTunnelState()
                if (state.isRunning && state.connectionData()?.isConnected == true) {
                    connected = true
                    break
                }
                delay(2000)
            }
            if (connected) {
                return true
            }
        }
        settingsStore.save(original)
        prefs.put(protocolKey, original.protocolSelection)
        prefs.put(beastKey, original.beastModeEnabled)
        return false
    }
}
