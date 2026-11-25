package com.skript.omnialbum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.skript.omnialbum.AppState
import com.skript.omnialbum.model.DisplayAlbum
import com.skript.omnialbum.model.PhotoProvider
import com.skript.omnialbum.viewmodel.AggregatorViewModel
import kotlinx.coroutines.launch

enum class AlbumViewMode { GRID, LIST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    appState: AppState,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = appState.aggregator
    val albums by viewModel.albums.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var searchText by remember { mutableStateOf("") }
    var viewMode by remember { mutableStateOf(AlbumViewMode.GRID) }
    var albumToDelete by remember { mutableStateOf<DisplayAlbum?>(null) }
    val scope = rememberCoroutineScope()

    val filtered = remember(albums, searchText) {
        if (searchText.isBlank()) albums
        else {
            val q = searchText.lowercase()
            albums.filter {
                it.albumName.lowercase().contains(q) || it.baseURL.lowercase().contains(q)
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = { scope.launch { } },
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search albums") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )

            when {
                isLoading && albums.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                albums.isEmpty() -> EmptyAlbumsView(onAdd = onAdd)
                else -> {
                    if (viewMode == AlbumViewMode.GRID) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filtered, key = { it.id }) { album ->
                                AlbumGridCell(
                                    album = album,
                                    onClick = { appState.openAlbum(album) },
                                    onLongPress = { albumToDelete = album }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filtered, key = { it.id }) { album ->
                                AlbumListCell(
                                    album = album,
                                    onClick = { appState.openAlbum(album) },
                                    onDelete = { albumToDelete = album }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } },
            text = { Text(errorMessage ?: "") }
        )
    }

    if (albumToDelete != null) {
        AlertDialog(
            onDismissRequest = { albumToDelete = null },
            title = { Text("Delete album?") },
            text = { Text("“${albumToDelete?.albumName}” will be removed from your list.") },
            confirmButton = {
                TextButton(onClick = {
                    val album = albumToDelete
                    albumToDelete = null
                    if (album != null) scope.launch { viewModel.deleteAlbum(album) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { albumToDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EmptyAlbumsView(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(16.dp))
        Text("Welcome to OmniAlbum", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "View shared albums from your favorite self-hosted photo services.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoProvider.entries.forEach { provider ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ProviderBadge(provider, 40)
                    Text(provider.displayName, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        androidx.compose.material3.Button(onClick = onAdd) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.size(8.dp))
            Text("Add Share Link")
        }
    }
}

@Composable
private fun AlbumGridCell(album: DisplayAlbum, onClick: () -> Unit, onLongPress: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .pointerInputWithLongPress(onClick, onLongPress)
        ) {
            CachedImage(
                model = AggregatorViewModel.thumbnailURL(album),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            ProviderBadge(
                album.provider,
                22,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            album.albumName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1
        )
        Text(
            albumCountLabel(album.assetCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun AlbumListCell(album: DisplayAlbum, onClick: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(album.albumName, maxLines = 2) },
        supportingContent = {
            Column {
                Text(albumCountLabel(album.assetCount))
                Text(hostOf(album.baseURL), style = MaterialTheme.typography.bodySmall)
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                CachedImage(
                    model = AggregatorViewModel.thumbnailURL(album),
                    modifier = Modifier.fillMaxSize()
                )
                ProviderBadge(
                    album.provider,
                    18,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                )
            }
        },
        trailingContent = {
            TextButton(onClick = onDelete) { Text("Delete") }
        },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInputWithLongPress(onClick, {})
    )
}

private fun androidx.compose.ui.Modifier.pointerInputWithLongPress(
    onClick: () -> Unit,
    onLongPress: () -> Unit
): androidx.compose.ui.Modifier = this.then(
    androidx.compose.foundation.combinedClickable(
        onClick = onClick,
        onLongClick = onLongPress
    )
)

fun albumCountLabel(count: Int?): String = when (count) {
    null -> "No photos"
    1 -> "1 Photo"
    else -> "$count Photos"
}

fun hostOf(baseURL: String): String =
    runCatching { io.ktor.http.Url(baseURL).host }.getOrNull() ?: baseURL

@Composable
fun ViewModeToggle(viewMode: AlbumViewMode, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (viewMode == AlbumViewMode.GRID) Icons.Default.List else Icons.Default.GridView,
            contentDescription = "Toggle view mode"
        )
    }
}
