package com.psiphon3.azadi

import android.content.Context
import android.os.Build
import com.psiphon3.BuildConfig
import com.psiphon3.TunnelState
import org.json.JSONArray
import org.json.JSONObject

object DebugReportExporter {
    fun export(
        context: Context,
        settings: AzadiSettings,
        tunnelState: TunnelState,
        connectionUi: ConnectionUiState
    ): String {
        val recentLogs = AzadiEventLogger.asText()
            .lines()
            .takeLast(200)

        val json = JSONObject()
            .put("app", JSONObject()
                .put("name", "AzadiTunnel")
                .put("version", BuildConfig.VERSION_NAME)
                .put("build", BuildConfig.VERSION_CODE))
            .put("device", JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("android", Build.VERSION.RELEASE)
                .put("sdk", Build.VERSION.SDK_INT))
            .put("transport", JSONObject()
                .put("protocol", settings.protocolSelection)
                .put("beastMode", settings.beastModeEnabled)
                .put("region", settings.egressRegion)
                .put("smartFallback", settings.smartFallbackChainEnabled))
            .put("connection", JSONObject()
                .put("running", tunnelState.isRunning)
                .put("connected", tunnelState.connectionData()?.isConnected == true)
                .put("protocol", connectionUi.connectedProtocol)
                .put("fallbackStep", connectionUi.fallbackStepLabel)
                .put("error", connectionUi.errorMessage))
            .put("diagnostics", JSONObject()
                .put("summary", connectionUi.diagnosticsSummary)
                .put("leak", connectionUi.leakSummary)
                .put("pingMs", connectionUi.pingMs)
                .put("quality", connectionUi.qualityReport?.let {
                    JSONObject()
                        .put("protocol", it.protocol)
                        .put("publicIp", it.publicIp)
                        .put("region", it.region)
                        .put("latencyMs", it.latencyMs)
                        .put("https204", it.https204Ok)
                }))
            .put("config", JSONObject()
                .put("summary", ConfigManager.configSummary(context))
                .put("hasCustom", ConfigManager.hasCustomConfig(context)))
            .put("recentLogs", JSONArray(recentLogs))

        return json.toString(2)
    }
}
