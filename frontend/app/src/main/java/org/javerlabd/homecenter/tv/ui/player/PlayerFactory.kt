package org.javerlabd.homecenter.tv.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds players that stream from the server.
 *
 * The point of routing playback through the app's OkHttp client is the bearer token:
 * /api/v1/media/{id}/stream is not public, and ExoPlayer's default data source would send
 * an unsigned request and get a 401. Range requests, which is what seeking is made of, are
 * handled by the server and need nothing special here.
 */
@Singleton
class PlayerFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {

    @OptIn(UnstableApi::class)
    fun create(): ExoPlayer {
        val dataSourceFactory: DataSource.Factory = OkHttpDataSource.Factory(client)

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            // Software fallback matters here: the library is whatever the household copied
            // onto the NAS, and a TV box without a hardware decoder for one old codec
            // should still play the file rather than fail.
            .setRenderersFactory(
                DefaultRenderersFactory(context)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            )
            .build()
    }
}
