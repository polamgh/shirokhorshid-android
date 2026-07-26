package com.psiphon3.azadi

import kotlinx.coroutines.delay

object SettingsReconnectHelper {
    private const val RECONNECT_DELAY_MS = 800L

    suspend fun reconnectIfConnected(
        isRunning: () -> Boolean,
        disconnect: suspend () -> Unit,
        connect: suspend () -> Unit
    ) {
        if (!isRunning()) return
        AzadiEventLogger.logSync("SETTINGS_RECONNECT_STARTED")
        disconnect()
        delay(RECONNECT_DELAY_MS)
        connect()
        AzadiEventLogger.logSync("SETTINGS_RECONNECT_FINISHED")
    }
}
