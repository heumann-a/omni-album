package com.skript.omnialbum.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

@OptIn(ExperimentalForeignApi::class)
class IosNetworkMonitor : NetworkMonitor {
    override val isConnected = MutableStateFlow(true)

    private var monitor: platform.Network.nw_path_monitor_t? = null
    private var queue: platform.darwin.dispatch_queue_t? = null

    override fun start() {
        val q = dispatch_queue_create("com.skript.omnialbum.networkmonitor", null)
        val m = nw_path_monitor_create()
        nw_path_monitor_set_queue(m, q)
        nw_path_monitor_set_update_handler(m) { path ->
            if (path != null) {
                isConnected.value = nw_path_get_status(path) == nw_path_status_satisfied
            }
        }
        nw_path_monitor_start(m)
        queue = q
        monitor = m
    }

    override fun stop() {
        monitor?.let { nw_path_monitor_cancel(it) }
        monitor = null
        queue = null
    }
}

actual fun createNetworkMonitor(): NetworkMonitor = IosNetworkMonitor()
