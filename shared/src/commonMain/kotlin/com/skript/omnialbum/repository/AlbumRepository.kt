package com.skript.omnialbum.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.executeAsList
import com.skript.omnialbum.db.Albums
import com.skript.omnialbum.db.DatabaseHolder
import com.skript.omnialbum.model.DisplayAlbum
import com.skript.omnialbum.model.PhotoProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class AlbumRepository private constructor() {

    private val queries get() = DatabaseHolder.database.albumsQueries

    fun albumsFlow(): Flow<List<DisplayAlbum>> =
        queries.selectAllOrdered().asFlow().map { it.executeAsList().map(::toDisplayAlbum) }

    suspend fun addAlbum(album: DisplayAlbum) {
        queries.insertAlbum(
            id = album.id,
            album_name = album.albumName,
            album_thumbnail_asset_id = album.albumThumbnailAssetId,
            base_url = album.baseURL,
            share_key = album.shareKey,
            album_id = album.albumId,
            asset_count = album.assetCount?.toLong(),
            allow_upload = if (album.allowUpload) 1L else 0L,
            allow_download = if (album.allowDownload) 1L else 0L,
            show_metadata = if (album.showMetadata) 1L else 0L,
            password = album.password,
            provider = album.provider.name.lowercase(),
            created_at = Clock.System.now().toString()
        )
    }

    suspend fun updateAlbumData(albumId: String, assetCount: Int, thumbnailId: String?) {
        queries.updateAlbumData(
            asset_count = assetCount.toLong(),
            album_thumbnail_asset_id = thumbnailId,
            album_id = albumId
        )
    }

    suspend fun deleteAlbum(id: String) {
        queries.deleteAlbum(id)
    }

    suspend fun contains(id: String): Boolean = queries.existsById(id).executeAsOne()

    private fun toDisplayAlbum(row: Albums): DisplayAlbum = DisplayAlbum(
        id = row.id,
        albumName = row.album_name,
        albumThumbnailAssetId = row.album_thumbnail_asset_id,
        baseURL = row.base_url,
        shareKey = row.share_key,
        albumId = row.album_id,
        assetCount = row.asset_count?.toInt(),
        allowUpload = row.allow_upload == 1L,
        allowDownload = row.allow_download == 1L,
        showMetadata = row.show_metadata == 1L,
        password = row.password,
        provider = PhotoProvider.entries.firstOrNull { it.name.lowercase() == row.provider }
            ?: PhotoProvider.IMMICH
    )

    companion object {
        val shared = AlbumRepository()
    }
}
