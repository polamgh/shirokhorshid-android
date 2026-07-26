package com.psiphon3.azadi

import com.psiphon3.psiphonlibrary.PsiphonConstants

/** iOS-parity egress region codes (empty / Any + 16 countries). */
object PsiphonRegionList {
    @JvmField
    val all: List<String> = listOf(
        PsiphonConstants.REGION_CODE_ANY,
        "US", "CA", "GB", "DE", "FR", "NL", "CH", "SE",
        "JP", "SG", "AU", "IR", "AE", "IN", "BR", "ZA"
    )
}
