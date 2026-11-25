package com.skript.omnialbum

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skript.omnialbum.ui.AddAlbumSheet
import com.skript.omnialbum.ui.AlbumDetailScreen
import com.skript.omnialbum.ui.AlbumsScreen
import com.skript.omnialbum.ui.SettingsScreen
import kotlinx.coroutines.delay

@Composable
fun App(appState: AppState = remember { AppState() }) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var showSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(1200)
                showSplash = false
            }

            val navStack by appState.navStack.collectAsState()
            val selectedTab by appState.selectedTab.collectAsState()
            val isConnected by appState.networkMonitor.isConnected.collectAsState()
            var showAddSheet by remember { mutableStateOf(false) }

            val current = navStack.last()

            Scaffold(
                topBar = { },
                bottomBar = {
                    if (current is Screen.AlbumDetail) return@Scaffold
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { appState.navigateTo(Screen.Albums) },
                            icon = { Icon(Icons.Default.PhotoLibrary, null) },
                            label = { Text("Albums") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { appState.navigateTo(Screen.Settings) },
                            icon = { Icon(Icons.Default.Settings, null) },
                            label = { Text("Settings") }
                        )
                    }
                }
            ) { padding ->
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)) {
                    when (val screen = current) {
                        Screen.Albums -> AlbumsScreen(
                            appState = appState,
                            onAdd = { showAddSheet = true }
                        )
                        is Screen.AlbumDetail -> AlbumDetailScreen(
                            appState = appState,
                            album = screen.album,
                            onBack = { appState.navigateUp() }
                        )
                        Screen.Settings -> SettingsScreen(appState = appState)
                    }

                    if (!isConnected) {
                        androidx.compose.material3.Card(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp),
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.WifiOff, null, modifier = Modifier.size(14.dp))
                                Text(
                                    "No internet connection",
                                    modifier = Modifier.padding(start = 8.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            if (showAddSheet) {
                AddAlbumSheet(appState = appState, onDismiss = { showAddSheet = false })
            }

            if (showSplash) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("OmniAlbum", style = MaterialTheme.typography.displaySmall)
                }
            }
        }
    }
}
