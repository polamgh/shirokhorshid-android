package com.psiphon3.azadi

object ProtocolDisplay {
    @JvmStatic
    fun format(raw: String?): String = ConnectedTunnelProtocolParser.displayName(raw)

    fun formatTransportSelection(selection: String): String = when (selection) {
        "auto" -> "Auto"
        "direct" -> "Direct"
        "cdn_fronting" -> "CDN"
        "conduit" -> "Conduit"
        else -> format(selection)
    }
}
