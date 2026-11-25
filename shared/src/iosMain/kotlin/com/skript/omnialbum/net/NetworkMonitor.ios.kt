package com.skript.omnialbum.net

import kotlinx.coroutines.flow.MutableStateFlow
import platform.Network.NWPathMonitor
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_release
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
class IosNetworkMonitor : NetworkMonitor {
    override val isConnected = MutableStateFlow(true)

    private var monitor: NWPathMonitor? = null
    private var queue: platform.darwin.dispatch_queue_t? = null

    override fun start() {
        val q = dispatch_queue_create("com.skript.omnialbum.networkmonitor", null)
        queue = q
        val m = nw_path_monitor_create()
        monitor = m
        nw_path_monitor_set_update_handler(m) { path ->
            if (path != null) {
                isConnected.value = platform.Network.nw_path_get_status(path) == nw_path_status_satisfied
            }
        }
        nw_path_monitor_start(m, q)
    }

    override fun stop() {
        monitor?.let { nw_path_monitor_cancel(it) }
        monitor = null
        queue?.let { dispatch_release(it) }
        queue = null
    }
}

actual fun createNetworkMonitor(): NetworkMonitor = IosNetworkMonitor()
