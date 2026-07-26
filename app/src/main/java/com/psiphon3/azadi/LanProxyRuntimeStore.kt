package com.psiphon3.azadi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LanProxyRuntimeStatus {
    STOPPED,
    RUNNING,
    VPN_DISCONNECTED,
    NO_WIFI_IP,
    PORT_IN_USE,
    FAILED_TO_START
}

data class LanProxyRuntimeState(
    val status: LanProxyRuntimeStatus = LanProxyRuntimeStatus.STOPPED,
    val boundHost: String = "",
    val httpPort: Int = 0,
    val socksPort: Int = 0,
    val proxyOnlyMode: Boolean = false
)

object LanProxyRuntimeStore {
    private val _state = MutableStateFlow(LanProxyRuntimeState())
    val state: StateFlow<LanProxyRuntimeState> = _state.asStateFlow()

    @JvmStatic
    fun onProxyBridgeStarted(
        boundHost: String,
        httpPort: Int,
        socksPort: Int,
        proxyOnlyMode: Boolean
    ) {
        _state.value = LanProxyRuntimeState(
            status = LanProxyRuntimeStatus.RUNNING,
            boundHost = boundHost,
            httpPort = httpPort,
            socksPort = socksPort,
            proxyOnlyMode = proxyOnlyMode
        )
    }

    @JvmStatic
    fun onVpnDisconnected() {
        _state.value = LanProxyRuntimeState(status = LanProxyRuntimeStatus.VPN_DISCONNECTED)
        AzadiEventLogger.logSync("LAN_PROXY_VPN_DISCONNECTED_STOP", null)
    }

    @JvmStatic
    fun onStopped() {
        _state.value = LanProxyRuntimeState(status = LanProxyRuntimeStatus.STOPPED)
    }

    @JvmStatic
    fun onNoWifiIp() {
        _state.value = LanProxyRuntimeState(status = LanProxyRuntimeStatus.NO_WIFI_IP)
        AzadiEventLogger.logSync("LAN_PROXY_WIFI_IP_MISSING", null)
    }

    @JvmStatic
    fun onPortInUse() {
        _state.value = _state.value.copy(status = LanProxyRuntimeStatus.PORT_IN_USE)
        AzadiEventLogger.logSync("LAN_PROXY_PORT_IN_USE", null)
    }

    @JvmStatic
    fun onFailedToStart() {
        _state.value = _state.value.copy(status = LanProxyRuntimeStatus.FAILED_TO_START)
    }

    @JvmStatic
    fun currentStatus(): String = _state.value.status.name.lowercase()
}
