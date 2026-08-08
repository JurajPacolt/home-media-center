package org.javerlabd.homecenter.tv.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.javerlabd.homecenter.tv.BuildConfig
import org.javerlabd.homecenter.tv.api.KniznicaApi
import org.javerlabd.homecenter.tv.api.PrihlasenieApi
import org.javerlabd.homecenter.tv.api.SkenovanieApi
import org.javerlabd.homecenter.tv.data.net.HomeCenterInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // The server may add fields the installed client does not know about yet. Failing
        // the whole response over one unknown key would make every API addition breaking.
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(interceptor: HomeCenterInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
                    )
                }
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            // Generous: the first byte of a file comes from Samba through the server, and a
            // spinning-disk NAS waking up is slower than a plain JSON call.
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(HomeCenterInterceptor.PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideLibraryApi(retrofit: Retrofit): KniznicaApi = retrofit.create(KniznicaApi::class.java)

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): PrihlasenieApi = retrofit.create(PrihlasenieApi::class.java)

    @Provides
    @Singleton
    fun provideScanApi(retrofit: Retrofit): SkenovanieApi = retrofit.create(SkenovanieApi::class.java)
}
