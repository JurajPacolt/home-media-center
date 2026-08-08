package org.javerland.homecenter.metadata;

import java.util.List;

import org.javerland.homecenter.media.ProviderGenre;
import org.jspecify.annotations.Nullable;

/**
 * Movie or series from Cinemeta. Unlike TMDb, a single request returns the whole series
 * including the episode list, so episodes need no further calls.
 */
record CinemetaTitle(
        String imdbId,
        String title,
        @Nullable String description,
        @Nullable String posterUrl,
        @Nullable Integer year,
        @Nullable Double rating,
        List<ProviderGenre> genres,
        List<CinemetaEpisode> episodes) {

    CinemetaTitle {
        genres = genres == null ? List.of() : List.copyOf(genres);
        episodes = episodes == null ? List.of() : List.copyOf(episodes);
    }

    /**
     * Numeric part of the IMDb identifier. {@code provider_id} in the index is a BIGINT, while
     * Cinemeta identifies titles as {@code tt0133093}; the prefix carries no information.
     */
    long numericId() {
        return Long.parseLong(imdbId.substring(2));
    }

    @Nullable CinemetaEpisode episode(int season, int episode) {
        return episodes.stream()
                .filter(candidate -> candidate.season() == season && candidate.episode() == episode)
                .findFirst()
                .orElse(null);
    }
}
