package com.skript.omnialbum.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.PlatformContext
import coil3.memory.MemoryCache
import com.skript.omnialbum.model.PhotoProvider
import com.skript.omnialbum.net.HttpClientHolder

fun providerColor(provider: PhotoProvider): Color = when (provider) {
    PhotoProvider.IMMICH -> Color(0xFF2196F3)
    PhotoProvider.NEXTCLOUD -> Color(0xFF00BCD4)
    PhotoProvider.PHOTOPRISM -> Color(0xFFFF9800)
    PhotoProvider.LYCHEE -> Color(0xFF4CAF50)
    PhotoProvider.ENTE -> Color(0xFF9C27B0)
}

fun imageLoader(): ImageLoader = ImageLoader.Builder(PlatformContext.INSTANCE)
    .components {
        add(KtorNetworkFetcherFactory { HttpClientHolder.client })
    }
    .memoryCache {
        MemoryCache.Builder()
            .maxSizeBytes(150 * 1024 * 1024)
            .build()
    }
    .build()

@Composable
fun ProviderBadge(provider: PhotoProvider, sizeDp: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape((sizeDp * 0.22).dp))
            .background(providerColor(provider)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = provider.displayName.first().toString(),
            color = Color.White,
            fontSize = (sizeDp * 0.55).sp,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun CachedImage(
    model: Any?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (model == null) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Photo,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = modifier,
            imageLoader = imageLoader(),
            contentScale = contentScale
        )
    }
}

@Composable
fun ZoomableImage(
    model: Any?,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale == 1f) {
            offset = Offset.Zero
        } else {
            offset += panChange
        }
    }

    CachedImage(
        model = model,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .transformable(state = transformState)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { reset() }
                )
            },
        contentScale = ContentScale.Fit
    )
}
