package com.psiphon3.azadi

import android.content.Context

/**
 * Loads Psiphon server entries bundled in [ASSET_NAME] inside the APK.
 * Used as offline fallback when remote server list download fails.
 */
object BundledServerEntries {
    const val ASSET_NAME = "psiphon-embedded-server-entries.txt"

    @Volatile
    private var cached: List<String>? = null

    @JvmStatic
    fun ensureLoaded(context: Context): List<String> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val lines = loadFromAssets(context.applicationContext)
            cached = lines
            AzadiEventLogger.logSync("PSIPHON_EMBEDDED_SERVER_ENTRIES_BUNDLED", "count=${lines.size}")
            return lines
        }
    }

    @JvmStatic
    fun lineCount(context: Context): Int = ensureLoaded(context).size

    /** Newline-separated server entries for Psiphon tunnel start. */
    @JvmStatic
    fun getServerEntriesText(context: Context): String {
        val lines = ensureLoaded(context)
        if (lines.isEmpty()) return ""
        return lines.joinToString("\n") + "\n"
    }

    private fun loadFromAssets(context: Context): List<String> {
        return try {
            context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
                reader.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toList()
            }.also { lines ->
                AzadiEventLogger.logSync(
                    "PSIPHON_EMBEDDED_SERVER_ENTRIES_LOADED",
                    "source=apk lines=${lines.size}"
                )
            }
        } catch (e: Exception) {
            AzadiEventLogger.logSync(
                "PSIPHON_EMBEDDED_SERVER_ENTRIES_LOADED",
                "source=apk lines=0 error=${e.message}"
            )
            emptyList()
        }
    }
}
