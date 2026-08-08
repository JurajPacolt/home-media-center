package org.javerland.homecenter.tv.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.javerland.homecenter.tv.data.db.HomeCenterDatabase
import org.javerland.homecenter.tv.data.db.PlaybackDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HomeCenterDatabase =
        Room.databaseBuilder(context, HomeCenterDatabase::class.java, "homecenter.db")
            // Resume positions are a convenience, not data worth a migration path. Losing
            // them on a schema change is cheaper than shipping a wrong migration.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun providePlaybackDao(database: HomeCenterDatabase): PlaybackDao = database.playbackDao()
}
