package org.javerlabd.homecenter.tv.ui.navigation

import org.javerlabd.homecenter.tv.domain.MediaCategory

/**
 * Routes carry an identifier and nothing else. Items are re-read from the repository or
 * taken from [org.javerlabd.homecenter.tv.data.repository.MediaQueue]; putting a whole
 * item into a route would mean encoding titles and paths into a URL.
 */
object Routes {

    const val SERVER = "server"
    const val LOGIN = "login"
    const val HOME = "home"
    const val SETTINGS = "settings"

    const val CATEGORY_ARG = "category"
    const val MEDIA_ID_ARG = "mediaId"

    /** Whether the detail screen asked to start over or to pick up where playback stopped. */
    const val FROM_START_ARG = "fromStart"

    const val BROWSE = "browse/{$CATEGORY_ARG}"
    const val DETAIL = "detail/{$MEDIA_ID_ARG}"
    const val VIDEO = "video/{$MEDIA_ID_ARG}/{$FROM_START_ARG}"
    const val PHOTO = "photo/{$MEDIA_ID_ARG}"
    const val MUSIC = "music/{$MEDIA_ID_ARG}"

    fun browse(category: MediaCategory) = "browse/${category.name}"

    fun detail(mediaId: Long) = "detail/$mediaId"

    fun video(mediaId: Long, fromStart: Boolean) = "video/$mediaId/$fromStart"

    fun photo(mediaId: Long) = "photo/$mediaId"

    fun music(mediaId: Long) = "music/$mediaId"
}
