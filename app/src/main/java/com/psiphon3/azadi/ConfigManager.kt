package com.psiphon3.azadi

import android.content.Context
import com.psiphon3.BuildConfig
import com.psiphon3.psiphonlibrary.EmbeddedValues
import net.grandcentrix.tray.AppPreferences
import org.json.JSONObject
import java.io.File

object ConfigManager {
    private const val CUSTOM_CONFIG_FILE = "azadi_custom_psiphon_config.json"
    private const val KEY_BOOTSTRAP_INSTALLED = "psiphon_bootstrap_installed"
    private const val KEY_USES_BUNDLED = "psiphon_uses_bundled_config"
    private const val KEY_BOOTSTRAP_VERSION = "psiphon_bootstrap_app_version"
    private const val PSIPHON_DIR = "psiphon"
    private const val SERVER_ENTRIES_FILE = "psiphon-embedded-server-entries.txt"

    fun installBundledConfigIfNeeded(context: Context, force: Boolean = false): Boolean {
        val appContext = context.applicationContext
        val prefs = AppPreferences(appContext)
        val version = BuildConfig.VERSION_CODE
        val lastVersion = prefs.getInt(KEY_BOOTSTRAP_VERSION, 0)
        val usesBundled = prefs.getBoolean(KEY_USES_BUNDLED, true)

        if (!force && prefs.getBoolean(KEY_BOOTSTRAP_INSTALLED, false) &&
            lastVersion == version && usesBundled && !hasCustomConfig(appContext)
        ) {
            return hasActiveConfig(appContext)
        }

        if (hasCustomConfig(appContext) && !force) {
            prefs.put(KEY_BOOTSTRAP_INSTALLED, true)
            prefs.put(KEY_USES_BUNDLED, false)
            return true
        }

        val dir = File(appContext.filesDir, PSIPHON_DIR)
        if (!dir.exists()) dir.mkdirs()

        val baseConfig = JSONObject().apply {
            put("PropagationChannelId", EmbeddedValues.PROPAGATION_CHANNEL_ID)
            put("SponsorId", EmbeddedValues.SPONSOR_ID)
            put("ClientVersion", EmbeddedValues.CLIENT_VERSION)
            if (EmbeddedValues.CONDUIT_COMPARTMENT_ID.isNotEmpty()) {
                put("InproxyClientPersonalCompartmentID", EmbeddedValues.CONDUIT_COMPARTMENT_ID)
            }
        }
        File(dir, "psiphon-config.json").writeText(baseConfig.toString(2))

        val bundledLines = BundledServerEntries.ensureLoaded(appContext)
        val gradleEntries = EmbeddedValues.EMBEDDED_SERVER_LIST.filter { it.isNotBlank() }
        val allEntries = (gradleEntries + bundledLines).distinct()
        if (allEntries.isNotEmpty()) {
            File(dir, SERVER_ENTRIES_FILE).writeText(allEntries.joinToString("\n"))
            AzadiEventLogger.logSync(
                "PSIPHON_SERVER_ENTRIES_LOADED",
                "source=apk lines=${allEntries.size}"
            )
        }

        val remoteJson = EmbeddedValues.REMOTE_SERVER_LIST_URLS_JSON
        if (remoteJson.isNotEmpty() && remoteJson != "[]") {
            val urlCount = try {
                org.json.JSONArray(remoteJson).length()
            } catch (_: Exception) {
                0
            }
            AzadiEventLogger.logSync("PSIPHON_REMOTE_SERVER_LIST_ENABLED", "urls=$urlCount")
            AzadiEventLogger.logSync("REMOTE_SERVER_LIST_CONFIG", remoteJson)
        }

        prefs.put(KEY_BOOTSTRAP_INSTALLED, true)
        prefs.put(KEY_USES_BUNDLED, true)
        prefs.put(KEY_BOOTSTRAP_VERSION, version)
        AzadiEventLogger.logSync("PSIPHON_BUNDLED_CONFIG_INSTALLED")
        AzadiEventLogger.logSync("PSIPHON_CONFIG_FOUND")
        return hasActiveConfig(appContext)
    }

    fun ensureBundledConfig(context: Context): Boolean =
        installBundledConfigIfNeeded(context, force = false)

    fun usesBundledConfig(context: Context): Boolean {
        if (hasCustomConfig(context)) return false
        return AppPreferences(context.applicationContext).getBoolean(KEY_USES_BUNDLED, true)
    }

    fun hasCustomConfig(context: Context): Boolean =
        File(context.filesDir, CUSTOM_CONFIG_FILE).exists()

    fun serverEntriesLineCount(context: Context): Int {
        val custom = File(context.filesDir, CUSTOM_CONFIG_FILE)
        if (custom.exists()) return 1
        val stored = File(File(context.filesDir, PSIPHON_DIR), SERVER_ENTRIES_FILE)
        if (stored.exists()) {
            return stored.readLines().count { it.isNotBlank() }
        }
        val bundled = BundledServerEntries.lineCount(context)
        if (bundled > 0) return bundled
        return EmbeddedValues.EMBEDDED_SERVER_LIST.filter { it.isNotBlank() }.size
    }

    fun hasActiveConfig(context: Context): Boolean {
        if (hasCustomConfig(context)) return true
        if (EmbeddedValues.REMOTE_SERVER_LIST_URLS_JSON.isNotEmpty() &&
            EmbeddedValues.REMOTE_SERVER_LIST_URLS_JSON != "[]"
        ) return true
        if (BundledServerEntries.lineCount(context) > 0) return true
        if (EmbeddedValues.EMBEDDED_SERVER_LIST.any { it.isNotBlank() }) return true
        val dir = File(context.filesDir, PSIPHON_DIR)
        return dir.exists() && dir.listFiles()?.isNotEmpty() == true
    }

    fun importConfig(context: Context, raw: String): ImportResult {
        return try {
            val json = JSONObject(raw.trim())
            if (!json.has("RemoteServerListURLs") && !json.has("TargetServerEntry")) {
                AzadiEventLogger.logSync("PSIPHON_CONFIG_VALIDATION_FAILED", "missing keys")
                return ImportResult(false, "Config must include RemoteServerListURLs or TargetServerEntry")
            }
            File(context.filesDir, CUSTOM_CONFIG_FILE).writeText(json.toString(2))
            val prefs = AppPreferences(context.applicationContext)
            prefs.put(KEY_USES_BUNDLED, false)
            prefs.put(KEY_BOOTSTRAP_INSTALLED, true)
            AzadiEventLogger.logSync("PSIPHON_CONFIG_IMPORTED")
            ImportResult(true, null)
        } catch (e: Exception) {
            AzadiEventLogger.logSync("PSIPHON_CONFIG_VALIDATION_FAILED", e.message)
            ImportResult(false, e.message ?: "Invalid JSON")
        }
    }

    fun configSummary(context: Context): String = when {
        hasCustomConfig(context) -> "Custom"
        usesBundledConfig(context) && hasActiveConfig(context) -> "Bundled"
        hasActiveConfig(context) -> "Bundled"
        else -> "No config"
    }

    fun embeddedEntriesSubtitle(context: Context): String {
        val count = serverEntriesLineCount(context)
        if (hasCustomConfig(context)) {
            return "Custom config file installed"
        }
        if (count > 0) {
            return "Server entries: $count (APK bundled)"
        }
        val json = EmbeddedValues.REMOTE_SERVER_LIST_URLS_JSON
        if (json.isEmpty() || json == "[]") return "No embedded server list"
        return try {
            val urlCount = org.json.JSONArray(json).length()
            "Remote server list URLs: $urlCount"
        } catch (_: Exception) {
            "Remote server list configured"
        }
    }

    fun retryBundledInstall(context: Context): Boolean {
        if (hasCustomConfig(context)) {
            File(context.filesDir, CUSTOM_CONFIG_FILE).delete()
        }
        val prefs = AppPreferences(context.applicationContext)
        prefs.put(KEY_USES_BUNDLED, true)
        return installBundledConfigIfNeeded(context, force = true)
    }
}

data class ImportResult(val success: Boolean, val error: String?)
