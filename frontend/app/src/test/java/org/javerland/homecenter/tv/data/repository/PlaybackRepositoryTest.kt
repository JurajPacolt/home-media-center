package org.javerland.homecenter.tv.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.javerland.homecenter.tv.data.db.PlaybackDao
import org.javerland.homecenter.tv.data.db.PlaybackPositionEntity
import org.javerland.homecenter.tv.domain.MediaCategory
import org.javerland.homecenter.tv.domain.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules about when a position is worth keeping are the only real logic in the client
 * that nothing else would catch: they decide whether "continue watching" sends a viewer to
 * the opening titles or the closing credits.
 */
class PlaybackRepositoryTest {

    private val dao = FakePlaybackDao()
    private val repository = PlaybackRepository(dao)

    @Test
    fun `a position in the middle of a film is offered`() = runTest {
        dao.rows[FILM.id] = position(positionMs = 20 * MINUTE, durationMs = 90 * MINUTE)

        val resume = repository.resumePoint(FILM.id)

        assertEquals(20 * MINUTE, resume?.positionMs)
    }

    @Test
    fun `the first few seconds are the same as not having watched it`() = runTest {
        dao.rows[FILM.id] = position(positionMs = 12_000, durationMs = 90 * MINUTE)

        assertNull(repository.resumePoint(FILM.id))
    }

    @Test
    fun `a film watched to the end is not offered again`() = runTest {
        dao.rows[FILM.id] = position(positionMs = 90 * MINUTE - 10_000, durationMs = 90 * MINUTE)

        assertNull(repository.resumePoint(FILM.id))
    }

    @Test
    fun `reaching the end forgets the position instead of storing it`() = runTest {
        dao.rows[FILM.id] = position(positionMs = 20 * MINUTE, durationMs = 90 * MINUTE)

        repository.save(FILM, positionMs = 90 * MINUTE - 5_000, durationMs = 90 * MINUTE)

        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `stopping early forgets the position rather than pinning it to the start`() = runTest {
        repository.save(FILM, positionMs = 5_000, durationMs = 90 * MINUTE)

        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `a stream of unknown length is still resumable`() = runTest {
        repository.save(FILM, positionMs = 20 * MINUTE, durationMs = 0)

        assertEquals(20 * MINUTE, repository.resumePoint(FILM.id)?.positionMs)
    }

    private fun position(positionMs: Long, durationMs: Long) = PlaybackPositionEntity(
        mediaId = FILM.id,
        title = FILM.title,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = 0L,
    )

    private class FakePlaybackDao : PlaybackDao {
        val rows = mutableMapOf<Long, PlaybackPositionEntity>()

        override suspend fun find(mediaId: Long) = rows[mediaId]

        override fun recent(limit: Int): Flow<List<PlaybackPositionEntity>> =
            flowOf(rows.values.sortedByDescending { it.updatedAt }.take(limit))

        override suspend fun save(position: PlaybackPositionEntity) {
            rows[position.mediaId] = position
        }

        override suspend fun forget(mediaId: Long) {
            rows.remove(mediaId)
        }

        override suspend fun forgetAll() = rows.clear()
    }

    private companion object {
        const val MINUTE = 60_000L

        val FILM = MediaItem(
            id = 42,
            category = MediaCategory.VIDEO,
            title = "Matrix",
            fileName = "matrix.mkv",
            relativePath = "filmy/matrix.mkv",
            extension = "mkv",
            sizeBytes = 2_000_000_000,
            contentType = "video/x-matroska",
            streamUrl = "http://server:8085/api/v1/media/42/stream",
            posterUrl = null,
            description = null,
            releaseYear = 1999,
            rating = null,
            kind = null,
            groupKey = null,
            groupTitle = null,
            seasonNumber = null,
            episodeNumber = null,
            partNumber = null,
            genres = emptyList(),
        )
    }
}
