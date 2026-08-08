package org.javerlabd.homecenter.tv.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The sequence label is what tells a viewer which episode a row is. Zero padding is the
 * point: without it S01E10 sorts before S01E02 anywhere the label is read rather than the
 * numbers.
 */
class MediaItemTest {

    @Test
    fun `an episode is labelled with its season and number`() {
        assertEquals("S01E02", episode(season = 1, episode = 2).sequenceLabel)
        assertEquals("S10E10", episode(season = 10, episode = 10).sequenceLabel)
    }

    @Test
    fun `an episode without a season still shows its number`() {
        assertEquals("E07", episode(season = null, episode = 7).sequenceLabel)
    }

    @Test
    fun `a numbered part is labelled as a part`() {
        assertEquals("Časť 2", video().copy(partNumber = 2).sequenceLabel)
    }

    @Test
    fun `a plain film has no sequence label`() {
        assertNull(video().sequenceLabel)
    }

    private fun episode(season: Int?, episode: Int) =
        video().copy(seasonNumber = season, episodeNumber = episode)

    private fun video() = MediaItem(
        id = 1,
        category = MediaCategory.VIDEO,
        title = "Dark",
        fileName = "dark.mkv",
        relativePath = "serialy/dark.mkv",
        extension = "mkv",
        sizeBytes = 1,
        contentType = "video/x-matroska",
        streamUrl = "http://server:8085/api/v1/media/1/stream",
        posterUrl = null,
        description = null,
        releaseYear = null,
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
