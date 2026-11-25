package com.skript.omnialbum.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Network.NWPathMonitor
import platform.Network.NWPathStatus
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
class IosNetworkMonitor : NetworkMonitor {
    override val isConnected = MutableStateFlow(true)

    private var monitor: NWPathMonitor? = null

    override fun start() {
        val m = NWPathMonitor()
        m.pathUpdateHandler = { path ->
            isConnected.value = path.status == NWPathStatus.Satisfied
        }
        m.start(queue = dispatch_get_main_queue())
        monitor = m
    }

    override fun stop() {
        monitor?.cancel()
        monitor = null
    }
}

actual fun createNetworkMonitor(): NetworkMonitor = IosNetworkMonitor()
