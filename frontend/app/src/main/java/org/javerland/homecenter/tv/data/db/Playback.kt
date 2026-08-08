package org.javerland.homecenter.tv.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Where a video was left off. This is the one piece of state the client owns: the server
 * indexes files, but it has no idea which of them anybody watched.
 */
@Entity(tableName = "playback_position")
data class PlaybackPositionEntity(
    @PrimaryKey val mediaId: Long,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

@Dao
interface PlaybackDao {

    @Query("SELECT * FROM playback_position WHERE mediaId = :mediaId")
    suspend fun find(mediaId: Long): PlaybackPositionEntity?

    @Query("SELECT * FROM playback_position ORDER BY updatedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<PlaybackPositionEntity>>

    @Upsert
    suspend fun save(position: PlaybackPositionEntity)

    @Query("DELETE FROM playback_position WHERE mediaId = :mediaId")
    suspend fun forget(mediaId: Long)

    @Query("DELETE FROM playback_position")
    suspend fun forgetAll()
}

@Database(entities = [PlaybackPositionEntity::class], version = 1, exportSchema = false)
abstract class HomeCenterDatabase : RoomDatabase() {
    abstract fun playbackDao(): PlaybackDao
}
