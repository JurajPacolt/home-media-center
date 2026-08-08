package org.javerland.homecenter.tv

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Posters are fetched through the same OkHttp client as everything else, so they carry the
 * bearer token and reach the right host. Coil's default client would get a 401 on every
 * poster, because /api/v1/media/{id}/poster is not public.
 */
@HiltAndroidApp
class HomeCenterApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.2).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("posters"))
                    .maxSizeBytes(POSTER_CACHE_BYTES)
                    .build()
            }
            .build()

    private companion object {
        /** Posters are small; a library of a few thousand still fits comfortably. */
        const val POSTER_CACHE_BYTES = 256L * 1024 * 1024
    }
}
