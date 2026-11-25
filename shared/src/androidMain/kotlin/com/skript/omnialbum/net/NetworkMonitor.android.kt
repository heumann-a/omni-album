package com.skript.omnialbum.net

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.skript.omnialbum.platform.appContext
import kotlinx.coroutines.flow.MutableStateFlow

class AndroidNetworkMonitor : NetworkMonitor {
    override val isConnected = MutableStateFlow(true)

    private val connectivityManager =
        appContext.getSystemService(ConnectivityManager::class.java)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            isConnected.value = true
        }

        override fun onLost(network: Network) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities == null || !connectivityManager.activeNetworkCapabilitiesHasInternet()) {
                isConnected.value = false
            }
        }
    }

    private fun ConnectivityManager.activeNetworkCapabilitiesHasInternet(): Boolean {
        val active = activeNetwork ?: return false
        val caps = getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        isConnected.value = connectivityManager.activeNetworkCapabilitiesHasInternet()
    }

    override fun stop() {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }
}

actual fun createNetworkMonitor(): NetworkMonitor = AndroidNetworkMonitor()
