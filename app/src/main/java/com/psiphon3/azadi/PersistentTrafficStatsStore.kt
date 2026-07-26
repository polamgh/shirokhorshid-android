package com.psiphon3.azadi

import android.content.Context
import com.psiphon3.R
import net.grandcentrix.tray.AppPreferences

class PersistentTrafficStatsStore(context: Context) {
    private val prefs = AppPreferences(context.applicationContext)
    private val downloadKey = context.getString(R.string.persistentTrafficDownloadPreference)
    private val uploadKey = context.getString(R.string.persistentTrafficUploadPreference)

    fun load(): Pair<Long, Long> =
        prefs.getLong(downloadKey, 0L) to prefs.getLong(uploadKey, 0L)

    fun save(downloadBytes: Long, uploadBytes: Long) {
        prefs.put(downloadKey, downloadBytes)
        prefs.put(uploadKey, uploadBytes)
    }

    /**
     * Adds session delta since [lastSessionDownload]/[lastSessionUpload] into lifetime totals.
     * Returns updated lifetime pair and new session baselines.
     */
    fun accumulateSessionDelta(
        lifetimeDownload: Long,
        lifetimeUpload: Long,
        sessionDownload: Long,
        sessionUpload: Long,
        lastSessionDownload: Long,
        lastSessionUpload: Long
    ): AccumulateResult {
        val downloadDelta = (sessionDownload - lastSessionDownload).coerceAtLeast(0L)
        val uploadDelta = (sessionUpload - lastSessionUpload).coerceAtLeast(0L)
        if (downloadDelta == 0L && uploadDelta == 0L) {
            return AccumulateResult(lifetimeDownload, lifetimeUpload, sessionDownload, sessionUpload, false)
        }
        val newDownload = lifetimeDownload + downloadDelta
        val newUpload = lifetimeUpload + uploadDelta
        save(newDownload, newUpload)
        return AccumulateResult(newDownload, newUpload, sessionDownload, sessionUpload, true)
    }

    data class AccumulateResult(
        val lifetimeDownload: Long,
        val lifetimeUpload: Long,
        val lastSessionDownload: Long,
        val lastSessionUpload: Long,
        val changed: Boolean
    )
}
