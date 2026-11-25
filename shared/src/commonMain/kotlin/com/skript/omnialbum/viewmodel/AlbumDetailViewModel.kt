package com.skript.omnialbum.viewmodel

import com.skript.omnialbum.model.AlbumDetail
import com.skript.omnialbum.model.Asset
import com.skript.omnialbum.model.DisplayAlbum
import com.skript.omnialbum.providers.AssetVariant
import com.skript.omnialbum.providers.ProviderRegistry
import com.skript.omnialbum.providers.ShareLinkProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class AlbumDetailViewModel(
    val album: DisplayAlbum,
    private val aggregator: AggregatorViewModel = AggregatorViewModel(),
    provider: ShareLinkProvider? = null
) {
    private val provider: ShareLinkProvider = provider
        ?: ProviderRegistry.shared.provider(album.provider)
        ?: ProviderRegistry.shared.provider(com.skript.omnialbum.model.PhotoProvider.IMMICH)!!

    private val _assets = MutableStateFlow<List<Asset>>(emptyList())
    val assets: StateFlow<List<Asset>> = _assets

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _albumDetails = MutableStateFlow<AlbumDetail?>(null)
    val albumDetails: StateFlow<AlbumDetail?> = _albumDetails

    private val _dateRangeText = MutableStateFlow<String?>(null)
    val dateRangeText: StateFlow<String?> = _dateRangeText

    val shareURL: String get() = "${album.baseURL}/share/${album.shareKey}"
    val ownerName: String? get() = _albumDetails.value?.ownerName
    val albumDescription: String? get() = _albumDetails.value?.description?.takeIf { it.isNotEmpty() }
    val allowUpload: Boolean get() = album.allowUpload
    val allowDownload: Boolean get() = album.allowDownload
    val showMetadata: Boolean get() = album.showMetadata
    val requiresDecryption: Boolean get() = provider.requiresClientDecryption

    val coverImageURL: String?
        get() {
            val thumbnailId = album.albumThumbnailAssetId?.takeIf { it.isNotEmpty() } ?: return null
            return provider.thumbnailURL(album.baseURL, thumbnailId, album.shareKey)
        }

    val coverPreviewURL: String?
        get() {
            val thumbnailId = album.albumThumbnailAssetId?.takeIf { it.isNotEmpty() } ?: return null
            val dummy = Asset(id = thumbnailId, type = com.skript.omnialbum.model.AssetType.IMAGE, originalFileName = "")
            return provider.previewURL(album.baseURL, dummy, album.shareKey)
        }

    suspend fun loadIfNeeded() {
        if (_assets.value.isEmpty() && !_isLoading.value) {
            _isLoading.value = true
            _errorMessage.value = null
            fetchAlbumData()
            _isLoading.value = false
        } else if (_assets.value.isNotEmpty() && !_isRefreshing.value) {
            _isRefreshing.value = true
            fetchAlbumData()
            _isRefreshing.value = false
        }
    }

    suspend fun refresh() {
        if (_isLoading.value || _isRefreshing.value) return
        _isRefreshing.value = true
        fetchAlbumData()
        _isRefreshing.value = false
    }

    private suspend fun fetchAlbumData() {
        try {
            val detail = provider.fetchAlbumDetail(album.baseURL, album.albumId, album.shareKey, album.password)
            _albumDetails.value = detail
            _assets.value = detail.assets
            _errorMessage.value = null
            aggregator.updateAlbumData(album.albumId, detail.assetCount, detail.thumbnailAssetId)
            _dateRangeText.value = computeDateRangeText(detail.assets)
        } catch (e: Exception) {
            if (_assets.value.isEmpty()) {
                _errorMessage.value = e.message ?: "Unknown error"
            }
        }
    }

    fun thumbnailURL(forAsset: Asset): String? =
        provider.thumbnailURL(album.baseURL, forAsset.id, album.shareKey)

    fun previewURL(forAsset: Asset): String? =
        provider.previewURL(album.baseURL, forAsset, album.shareKey)

    fun originalURL(forAsset: Asset): String? =
        provider.originalURL(album.baseURL, forAsset, album.shareKey)

    fun videoPlaybackURL(forAsset: Asset): String? =
        provider.videoPlaybackURL(album.baseURL, forAsset, album.shareKey)

    suspend fun loadImageData(forAsset: Asset, variant: AssetVariant = AssetVariant.PREVIEW): ByteArray? {
        if (!provider.requiresClientDecryption) return null
        return provider.fetchDecryptedData(album.baseURL, forAsset, album.shareKey, variant)
    }

    private fun computeDateRangeText(assets: List<Asset>): String? {
        if (assets.isEmpty()) return null
        val dates = assets.mapNotNull { a ->
            a.createdAt?.takeIf { it.length >= 7 }?.let { monthYearOf(it) }
        }
        if (dates.isEmpty()) return null
        val (minMonth, minYear) = dates.minWith(compareBy({ it.second }, { it.first }))
        val (maxMonth, maxYear) = dates.maxWith(compareBy({ it.second }, { it.first }))
        val minMonthName = MONTH_NAMES[minMonth - 1]
        val maxMonthName = MONTH_NAMES[maxMonth - 1]
        return when {
            minYear == maxYear && minMonth == maxMonth -> "$minMonthName $minYear"
            minYear == maxYear -> "$minMonthName - $maxMonthName $maxYear"
            else -> "$minMonthName $minYear - $maxMonthName $maxYear"
        }
    }

    private fun monthYearOf(iso: String): Pair<Int, Int>? {
        val year = iso.take(4).toIntOrNull() ?: return null
        val month = iso.substring(5, 7).toIntOrNull() ?: return null
        return month to year
    }

    private companion object {
        val MONTH_NAMES = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    }
}
