package com.skript.omnialbum

import com.skript.omnialbum.deeplink.DeepLinkParser
import com.skript.omnialbum.deeplink.DeepLinkTarget
import com.skript.omnialbum.model.DisplayAlbum
import com.skript.omnialbum.net.NetworkMonitor
import com.skript.omnialbum.net.createNetworkMonitor
import com.skript.omnialbum.viewmodel.AggregatorViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface Screen {
    data object Albums : Screen
    data class AlbumDetail(val album: DisplayAlbum) : Screen
    data object Settings : Screen
}

class AppState {
    val aggregator = AggregatorViewModel()
    val networkMonitor: NetworkMonitor = createNetworkMonitor()

    private val _navStack = MutableStateFlow<List<Screen>>(listOf(Screen.Albums))
    val navStack: StateFlow<List<Screen>> = _navStack

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    private val _deepLink = MutableStateFlow<DeepLinkTarget?>(null)
    val deepLink: StateFlow<DeepLinkTarget?> = _deepLink

    val currentScreen: Screen get() = _navStack.value.last()

    init {
        networkMonitor.start()
    }

    fun handleDeepLink(url: String) {
        val target = DeepLinkParser.parse(url) ?: return
        _selectedTab.value = 0
        _deepLink.value = target
    }

    fun consumeDeepLink(): DeepLinkTarget? {
        val t = _deepLink.value
        _deepLink.value = null
        return t
    }

    fun openAlbum(album: DisplayAlbum) {
        _navStack.value = listOf(Screen.Albums, Screen.AlbumDetail(album))
    }

    fun navigateTo(screen: Screen) {
        if (screen is Screen.AlbumDetail) {
            _navStack.value = listOf(Screen.Albums, screen)
        } else {
            _navStack.value = listOf(screen)
            _selectedTab.value = if (screen is Screen.Settings) 1 else 0
        }
    }

    fun navigateUp() {
        if (_navStack.value.size > 1) {
            _navStack.value = _navStack.value.dropLast(1)
        }
    }
}
