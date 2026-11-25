package com.skript.omnialbum.viewmodel

import com.skript.omnialbum.model.DisplayAlbum
import com.skript.omnialbum.model.PhotoProvider
import com.skript.omnialbum.providers.ProviderRegistry
import com.skript.omnialbum.repository.AlbumRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AggregatorViewModel(
    private val repository: AlbumRepository = AlbumRepository.shared,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
) {
    private val _albums = MutableStateFlow<List<DisplayAlbum>>(emptyList())
    val albums: StateFlow<List<DisplayAlbum>> = _albums

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        scope.launch {
            repository.albums.collect { _albums.value = it }
            _isLoading.value = false
        }
    }


    suspend fun addAlbum(shareLinkURL: String, providerType: PhotoProvider, password: String? = null) {
        _errorMessage.value = null
        _isLoading.value = true
        try {
            val provider = ProviderRegistry.shared.provider(providerType)
                ?: throw IllegalStateException("Unsupported provider.")
            val parsed = provider.parseShareLink(shareLinkURL)
            val info = provider.fetchSharedLinkInfo(
                baseURL = parsed.baseURL,
                key = parsed.key,
                slug = parsed.slug,
                token = parsed.token,
                password = password
            )
            val display = DisplayAlbum(
                id = info.linkId,
                albumName = info.albumName,
                albumThumbnailAssetId = info.thumbnailAssetId,
                baseURL = parsed.baseURL,
                shareKey = info.shareKey,
                albumId = info.albumId,
                assetCount = info.assetCount,
                allowUpload = info.allowUpload,
                allowDownload = info.allowDownload,
                showMetadata = info.showMetadata,
                password = password,
                provider = provider.providerType
            )
            if (repository.contains(display.id)) {
                _errorMessage.value = "This album is already added."
                return
            }
            repository.addAlbum(display)
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Unknown error"
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun deleteAlbum(album: DisplayAlbum) {
        repository.deleteAlbum(album.id)
    }

    suspend fun updateAlbumData(albumId: String, assetCount: Int, thumbnailId: String?) {
        repository.updateAlbumData(albumId, assetCount, thumbnailId)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    companion object {
        fun thumbnailURL(forAlbum: DisplayAlbum): String? {
            val assetId = forAlbum.albumThumbnailAssetId?.takeIf { it.isNotEmpty() } ?: return null
            val provider = ProviderRegistry.shared.provider(forAlbum.provider) ?: return null
            return provider.thumbnailURL(
                baseURL = forAlbum.baseURL,
                assetId = assetId,
                shareKey = forAlbum.shareKey
            )
        }
    }
}
