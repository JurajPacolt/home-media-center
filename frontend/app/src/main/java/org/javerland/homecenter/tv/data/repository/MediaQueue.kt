package org.javerland.homecenter.tv.data.repository

import org.javerland.homecenter.tv.domain.MediaItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The list the user is currently looking at, kept so the photo viewer and the music player
 * can move to the next item without another request.
 *
 * Navigation arguments carry an id and nothing else. Passing a few hundred items through a
 * route would mean serialising the whole library into a string, and re-fetching the same
 * page on every skip would make pressing "next" wait on the network.
 */
@Singleton
class MediaQueue @Inject constructor() {

    @Volatile
    private var items: List<MediaItem> = emptyList()

    fun replaceWith(newItems: List<MediaItem>) {
        items = newItems
    }

    fun snapshot(): List<MediaItem> = items

    fun indexOf(mediaId: Long): Int = items.indexOfFirst { it.id == mediaId }

    fun itemAt(index: Int): MediaItem? = items.getOrNull(index)
}
