package org.javerlabd.homecenter.tv.domain

/**
 * What the client works with. The generated API models mirror the server's JSON, where
 * every field is optional; these types are the same data once it has been checked, with
 * addresses already resolved against the configured server.
 */

/** The three tiles the TV shows. Mirrors the server's MediaCategory. */
enum class MediaCategory {
    VIDEO,
    PHOTO,
    AUDIO,
}

/** Finer video classification. Does not affect the three categories. */
enum class VideoKind {
    MOVIE,
    TV_EPISODE,
}

data class Genre(val id: Long, val name: String)

data class MediaItem(
    val id: Long,
    val category: MediaCategory,
    val title: String,
    val fileName: String,
    val relativePath: String,
    val extension: String,
    val sizeBytes: Long,
    val contentType: String,
    /** Absolute address with Range request support; ExoPlayer streams straight from it. */
    val streamUrl: String,
    val posterUrl: String?,
    val description: String?,
    val releaseYear: Int?,
    val rating: Double?,
    val kind: VideoKind?,
    val groupKey: String?,
    val groupTitle: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val partNumber: Int?,
    val genres: List<Genre>,
) {

    /** True when this item belongs with others under a shared series or collection. */
    val isGrouped: Boolean get() = groupKey != null

    /** "S01E02" for an episode, "Časť 2" for a numbered part, null otherwise. */
    val sequenceLabel: String?
        get() = when {
            seasonNumber != null && episodeNumber != null ->
                "S%02dE%02d".format(seasonNumber, episodeNumber)
            episodeNumber != null -> "E%02d".format(episodeNumber)
            partNumber != null -> "Časť $partNumber"
            else -> null
        }
}

data class MediaPage(
    val items: List<MediaItem>,
    val total: Long,
    val limit: Int,
    val offset: Int,
) {
    val hasMore: Boolean get() = offset + items.size < total
}

data class CategoryTile(
    val category: MediaCategory,
    val label: String,
    val count: Long,
)

data class LibrarySummary(
    val tiles: List<CategoryTile>,
    val totalItems: Long,
    val totalSizeBytes: Long,
    val lastScanFinishedAt: String?,
) {
    fun count(category: MediaCategory): Long =
        tiles.firstOrNull { it.category == category }?.count ?: 0L
}

data class Account(
    val id: Long,
    val username: String,
    val displayName: String,
    val isAdmin: Boolean,
)
