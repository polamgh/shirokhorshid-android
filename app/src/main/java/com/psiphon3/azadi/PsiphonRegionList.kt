package com.psiphon3.azadi

import android.content.Context
import com.psiphon3.psiphonlibrary.PsiphonConstants
import com.psiphon3.psiphonlibrary.RegionListPreference
import com.psiphon3.psiphonlibrary.SharedPreferenceUtils
import net.grandcentrix.tray.AppPreferences

/** iOS-parity egress region codes (empty / Any + 16 countries). */
object PsiphonRegionList {
    @JvmField
    val all: List<String> = listOf(
        PsiphonConstants.REGION_CODE_ANY,
        "US", "CA", "GB", "DE", "FR", "NL", "CH", "SE",
        "JP", "SG", "AU", "IR", "AE", "IN", "BR", "ZA"
    )

    /** All countries for which we have assets (names/flags). */
    val allSupported: List<String> = listOf(
        PsiphonConstants.REGION_CODE_ANY,
        "AE", "AR", "AT", "AU", "BE", "BG", "BR", "CA", "CH", "CL", "CO", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "GB", "GR", "HK", "HR", "HU", "ID", "IE", "IN", "IS", "IT", "JP", "KE", "KR", "LT", "LV", "MX", "MY", "NL", "NO", "NZ", "PL", "PT", "RO", "RS", "SE", "SG", "SK", "TW", "UA", "US", "ZA"
    )

    /** Returns currently available regions from preferences, falling back to all supported if empty. */
    fun getAvailable(context: Context): List<String> {
        val prefs = AppPreferences(context.applicationContext)
        val raw = prefs.getString(RegionListPreference.KNOWN_REGIONS_PREFERENCE, "")
        if (raw.isNullOrBlank()) return allSupported
        
        val availableSet = SharedPreferenceUtils.deserializeSet(raw)
        // Sort alphabetically but keep "Any" at the top
        val sorted = availableSet.filter { it != PsiphonConstants.REGION_CODE_ANY }.sorted().toMutableList()
        sorted.add(0, PsiphonConstants.REGION_CODE_ANY)
        return sorted
    }
}
