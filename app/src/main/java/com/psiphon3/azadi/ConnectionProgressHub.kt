package com.psiphon3.azadi

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/** Dashboard reads shared tunnel statistics via this facade. */
object ConnectionProgressHub {
    val state: StateFlow<TunnelStatistics>
        get() = TunnelStatisticsStore.flow

    @JvmStatic
    fun init(context: Context) = TunnelStatisticsStore.init(context)

    @JvmStatic
    fun reset(@Suppress("UNUSED_PARAMETER") showConduitPanel: Boolean) = Unit

    @JvmStatic
    fun onConduitStep(context: Context, step: String) {
        TunnelStatisticsStore.setConduitStatusLineParsed(context, step)
    }

    @JvmStatic
    fun onTunnelProtocol(context: Context, rawProtocol: String) {
        TunnelStatisticsStore.setConnectedTunnelProtocol(context, rawProtocol)
    }

    @JvmStatic
    fun clear(context: Context) {
        TunnelStatisticsStore.markDisconnected(context)
    }
}
