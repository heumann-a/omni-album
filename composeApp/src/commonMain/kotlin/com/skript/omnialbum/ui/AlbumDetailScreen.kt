package com.skript.omnialbum.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.skript.omnialbum.AppState
import com.skript.omnialbum.model.Asset
import com.skript.omnialbum.model.DisplayAlbum
import com.skript.omnialbum.viewmodel.AlbumDetailViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    appState: AppState,
    album: DisplayAlbum,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = remember(album.id) { AlbumDetailViewModel(album, appState.aggregator) }
    val assets by viewModel.assets.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val dateRange by viewModel.dateRangeText.collectAsState()
    val details by viewModel.albumDetails.collectAsState()

    var lightboxIndex by remember { mutableStateOf<Int?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(album.id) {
        val target = appState.consumeDeepLink()
        viewModel.loadIfNeeded()
        if (target != null && target.albumId == album.albumId) {
            val idx = viewModel.assets.value.indexOfFirst { it.id == target.assetId }
            lightboxIndex = if (idx >= 0) idx else 0.takeIf { assets.isNotEmpty() }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(album.albumName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { viewModel.refresh() } }) {
                        if (isLoading || isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Album info")
                    }
                }
            )
        }
    ) { padding ->
        when {
            errorMessage != null && assets.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load photos", style = MaterialTheme.typography.titleMedium)
                    Text(errorMessage ?: "", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.Button(onClick = { scope.launch { viewModel.loadIfNeeded() } }) {
                        Text("Try Again")
                    }
                }
            }
            assets.isEmpty() && !isLoading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("This album has no photos yet.")
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = 2.dp,
                    end = 2.dp,
                    top = padding.calculateTopPadding(),
                    bottom = 2.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "header") {
                    AlbumHeader(
                        album = album,
                        viewModel = viewModel,
                        dateRange = dateRange,
                        ownerName = viewModel.ownerName
                    )
                }
                itemsIndexed(assets, key = { _, asset -> asset.id }) { index, asset ->
                    AssetCell(
                        asset = asset,
                        url = viewModel.thumbnailURL(asset),
                        onClick = { lightboxIndex = index }
                    )
                }
            }
        }
    }

    if (lightboxIndex != null && assets.isNotEmpty()) {
        LightboxScreen(
            viewModel = viewModel,
            assets = assets,
            initialIndex = lightboxIndex ?: 0,
            onDismiss = { lightboxIndex = null }
        )
    }

    if (showInfo) {
        ModalBottomSheet(
            onDismissRequest = { showInfo = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(album.albumName, style = MaterialTheme.typography.titleLarge)
                details?.description?.takeIf { it.isNotEmpty() }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                InfoRow("Photos", album.assetCount?.toString() ?: "—")
                InfoRow("Provider", album.provider.displayName)
                InfoRow("Server", hostOf(album.baseURL))
                InfoRow("Shared by", viewModel.ownerName ?: "—")
                InfoRow("Uploads allowed", if (album.allowUpload) "Yes" else "No")
                InfoRow("Downloads allowed", if (album.allowDownload) "Yes" else "No")
                InfoRow("Show metadata", if (album.showMetadata) "Yes" else "No")
                InfoRow("Password protected", if (album.password != null) "Yes" else "No")
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AlbumHeader(
    album: DisplayAlbum,
    viewModel: AlbumDetailViewModel,
    dateRange: String?,
    ownerName: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        CachedImage(
            model = viewModel.coverPreviewURL ?: viewModel.coverImageURL,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            Text(
                album.albumName,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            ownerName?.let {
                Text("Shared by $it", color = Color.White.copy(alpha = 0.95f), style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProviderBadge(album.provider, 20)
                album.assetCount?.let {
                    Spacer(Modifier.size(8.dp))
                    Text("$it photos", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                }
                dateRange?.let {
                    Spacer(Modifier.size(8.dp))
                    Text("• $it", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun AssetCell(asset: Asset, url: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
    ) {
        CachedImage(model = url, modifier = Modifier.fillMaxSize())
        if (asset.type == com.skript.omnialbum.model.AssetType.VIDEO) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = "Video",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp),
                tint = Color.White
            )
        }
    }
}


