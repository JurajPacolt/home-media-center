package org.javerland.homecenter.tv.data.repository

import org.javerland.homecenter.tv.api.KniznicaApi
import org.javerland.homecenter.tv.api.model.CategoryTileDto
import org.javerland.homecenter.tv.api.model.LibrarySummaryDto
import org.javerland.homecenter.tv.api.model.MediaGenreDto
import org.javerland.homecenter.tv.api.model.MediaItemDto
import org.javerland.homecenter.tv.api.model.MediaMetadataDto
import org.javerland.homecenter.tv.data.net.apiCall
import org.javerland.homecenter.tv.data.net.bodyOrThrow
import org.javerland.homecenter.tv.data.session.SessionStore
import org.javerland.homecenter.tv.domain.CategoryTile
import org.javerland.homecenter.tv.domain.Genre
import org.javerland.homecenter.tv.domain.LibrarySummary
import org.javerland.homecenter.tv.domain.MediaCategory
import org.javerland.homecenter.tv.domain.MediaItem
import org.javerland.homecenter.tv.domain.MediaPage
import org.javerland.homecenter.tv.domain.VideoKind
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the library from the server. Everything here comes out of the server's index—the
 * client never sees Samba, and a listing costs one HTTP call.
 *
 * This is also where the generated models stop. They mirror the JSON, where every field is
 * optional; past this point the app works with types that say what is actually there.
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val api: KniznicaApi,
    private val sessionStore: SessionStore,
) {

    suspend fun summary(): LibrarySummary = apiCall {
        api.library().bodyOrThrow().toDomain()
    }

    suspend fun page(
        category: MediaCategory?,
        genreId: Long? = null,
        search: String? = null,
        limit: Int = PAGE_SIZE,
        offset: Int = 0,
    ): MediaPage = apiCall {
        val response = api.list(
            category = category?.let { KniznicaApi.CategoryList.valueOf(it.name) },
            sourceId = null,
            genreId = genreId,
            search = search?.takeIf { it.isNotBlank() },
            limit = limit,
            offset = offset,
        ).bodyOrThrow()

        MediaPage(
            items = response.items.orEmpty().mapNotNull { it.toDomain() },
            total = response.total ?: 0L,
            limit = response.limit ?: limit,
            offset = response.offset ?: offset,
        )
    }

    suspend fun item(id: Long): MediaItem = apiCall {
        api.detail(id).bodyOrThrow().toDomain()
            ?: throw IllegalStateException("Server vrátil položku bez identifikátora")
    }

    suspend fun genres(): List<Genre> = apiCall {
        api.genres().bodyOrThrow().mapNotNull { it.toDomain() }
    }

    /**
     * Everything the server returns is relative to its own root, and the client knows the
     * root only at runtime. ExoPlayer and Coil both need the whole address.
     */
    private fun absolute(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val server = sessionStore.snapshot().serverUrl?.trimEnd('/') ?: return null
        return if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            server + if (path.startsWith("/")) path else "/$path"
        }
    }

    private fun LibrarySummaryDto.toDomain() = LibrarySummary(
        tiles = categories.orEmpty().mapNotNull { it.toDomain() },
        totalItems = totalItems ?: 0L,
        totalSizeBytes = totalSizeBytes ?: 0L,
        lastScanFinishedAt = lastScan?.finishedAt,
    )

    private fun CategoryTileDto.toDomain(): CategoryTile? {
        val category = category?.toDomain() ?: return null
        return CategoryTile(category, label ?: category.name, count ?: 0L)
    }

    private fun CategoryTileDto.Category.toDomain() = MediaCategory.valueOf(value)

    private fun MediaGenreDto.toDomain(): Genre? {
        val id = id ?: return null
        return Genre(id, name.orEmpty())
    }

    private fun MediaItemDto.toDomain(): MediaItem? {
        val id = id ?: return null
        val streamUrl = absolute(streamUrl ?: "/api/v1/media/$id/stream") ?: return null
        return MediaItem(
            id = id,
            category = MediaCategory.valueOf((category ?: MediaItemDto.Category.VIDEO).value),
            title = title ?: fileName.orEmpty(),
            fileName = fileName.orEmpty(),
            relativePath = relativePath.orEmpty(),
            extension = extension.orEmpty(),
            sizeBytes = sizeBytes ?: 0L,
            contentType = contentType ?: "application/octet-stream",
            streamUrl = streamUrl,
            posterUrl = absolute(metadata?.posterUrl),
            description = metadata?.description,
            releaseYear = metadata?.releaseYear,
            rating = metadata?.rating,
            kind = metadata?.kind?.toDomain(),
            groupKey = metadata?.groupKey,
            groupTitle = metadata?.groupTitle,
            seasonNumber = metadata?.seasonNumber,
            episodeNumber = metadata?.episodeNumber,
            partNumber = metadata?.partNumber,
            genres = metadata?.genres.orEmpty().mapNotNull { it.toDomain() },
        )
    }

    private fun MediaMetadataDto.Kind.toDomain() = VideoKind.valueOf(value)

    companion object {
        /** One screenful of a grid plus enough headroom that scrolling does not stutter. */
        const val PAGE_SIZE = 60
    }
}
