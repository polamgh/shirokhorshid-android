package com.psiphon3.azadi

object BundledIranCIDR {
    @JvmStatic
    fun routes(context: android.content.Context): List<BypassRoute> =
        IranBypassListService.effectiveRoutes(context)

    val routes: List<BypassRoute> by lazy {
        BypassRoutes.parseBlob(
            """
            2.144.0.0/14
            5.52.0.0/16
            31.24.0.0/13
            37.98.0.0/16
            46.32.0.0/19
            78.39.0.0/16
            79.175.128.0/18
            85.185.0.0/16
            91.106.0.0/16
            94.182.0.0/16
            109.122.192.0/18
            178.22.72.0/21
            185.4.16.0/22
            188.121.0.0/16
            193.189.122.0/23
            """.trimIndent()
        )
    }
}
