package org.javerlabd.homecenter.tv.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.javerlabd.homecenter.tv.data.db.PlaybackDao
import org.javerlabd.homecenter.tv.data.db.PlaybackPositionEntity
import org.javerlabd.homecenter.tv.domain.MediaItem
import javax.inject.Inject
import javax.inject.Singleton

/** Where playback stopped, and whether resuming there is worth offering. */
data class ResumePoint(
    val mediaId: Long,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
)

@Singleton
class PlaybackRepository @Inject constructor(
    private val dao: PlaybackDao,
) {

    /**
     * A resume point is only interesting in the middle of a file. The first few seconds are
     * indistinguishable from starting over, and the last minute means it was watched to the
     * end—offering "continue" there would land the viewer on the closing credits.
     */
    suspend fun resumePoint(mediaId: Long): ResumePoint? {
        val saved = dao.find(mediaId) ?: return null
        if (saved.positionMs < MINIMUM_RESUME_MS) return null
        if (saved.durationMs > 0 && saved.positionMs > saved.durationMs - FINISHED_TAIL_MS) return null
        return ResumePoint(saved.mediaId, saved.title, saved.positionMs, saved.durationMs)
    }

    fun recent(limit: Int = 10): Flow<List<ResumePoint>> = dao.recent(limit).map { rows ->
        rows.filter { it.positionMs >= MINIMUM_RESUME_MS }
            .filter { it.durationMs <= 0 || it.positionMs <= it.durationMs - FINISHED_TAIL_MS }
            .map { ResumePoint(it.mediaId, it.title, it.positionMs, it.durationMs) }
    }

    suspend fun save(item: MediaItem, positionMs: Long, durationMs: Long) {
        // Watched to the end: drop the row instead of storing a position nobody will use.
        if (durationMs > 0 && positionMs > durationMs - FINISHED_TAIL_MS) {
            dao.forget(item.id)
            return
        }
        if (positionMs < MINIMUM_RESUME_MS) {
            dao.forget(item.id)
            return
        }
        dao.save(
            PlaybackPositionEntity(
                mediaId = item.id,
                title = item.title,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun forgetAll() = dao.forgetAll()

    private companion object {
        const val MINIMUM_RESUME_MS = 30_000L
        const val FINISHED_TAIL_MS = 60_000L
    }
}
