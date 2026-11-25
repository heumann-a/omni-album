package com.skript.omnialbum.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skript.omnialbum.model.Asset
import com.skript.omnialbum.model.AssetType
import com.skript.omnialbum.providers.AssetVariant
import com.skript.omnialbum.viewmodel.AlbumDetailViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LightboxScreen(
    viewModel: AlbumDetailViewModel,
    assets: List<Asset>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, assets.lastIndex)) { assets.size }
    var showExif by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val asset = assets[page]
            var imageModel by remember(asset.id) { mutableStateOf<Any?>(null) }
            var loading by remember(asset.id) { mutableStateOf(false) }

            LaunchedEffect(asset.id) {
                loading = true
                imageModel = if (viewModel.requiresDecryption) {
                    runCatching { viewModel.loadImageData(asset, AssetVariant.PREVIEW) }.getOrNull()
                } else {
                    viewModel.previewURL(asset) ?: viewModel.thumbnailURL(asset)
                }
                loading = false
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    asset.type == AssetType.VIDEO -> {
                        CachedImage(model = viewModel.thumbnailURL(asset), modifier = Modifier.fillMaxSize())
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = "Play video",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(64.dp),
                            tint = Color.White
                        )
                    }
                    else -> ZoomableImage(model = imageModel, modifier = Modifier.fillMaxSize())
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            if (viewModel.showMetadata) {
                IconButton(onClick = { showExif = true }) {
                    Icon(Icons.Default.Info, contentDescription = "Details", tint = Color.White)
                }
            }
        }

        Text(
            "${pagerState.currentPage + 1} / ${assets.size}",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    if (showExif && assets.isNotEmpty()) {
        val asset = assets[pagerState.currentPage.coerceIn(0, assets.lastIndex)]
        ModalBottomSheet(
            onDismissRequest = { showExif = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(asset.originalFileName, style = MaterialTheme.typography.titleMedium)
                val exif = asset.exif
                if (exif == null) {
                    Text("No EXIF metadata", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Spacer(Modifier.height(8.dp))
                    exif.make?.let { InfoRow("Camera", it) }
                    exif.model?.let { InfoRow("Model", it) }
                    exif.lensModel?.let { InfoRow("Lens", it) }
                    exif.fNumber?.let { InfoRow("Aperture", "f/${it}") }
                    exif.focalLength?.let { InfoRow("Focal length", "${it} mm") }
                    exif.iso?.let { InfoRow("ISO", it.toString()) }
                    exif.exposureTime?.let { InfoRow("Shutter", it) }
                    if (exif.width != null && exif.height != null) {
                        InfoRow("Resolution", "${exif.width} × ${exif.height}")
                    }
                    exif.fileSizeInBytes?.let { InfoRow("File size", formatBytes(it)) }
                    if (exif.latitude != null && exif.longitude != null) {
                        InfoRow("Location", "${exif.latitude}, ${exif.longitude}")
                        exif.city?.let { InfoRow("City", it) }
                        exif.country?.let { InfoRow("Country", it) }
                    }
                    exif.description?.let { InfoRow("Description", it) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
