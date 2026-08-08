package org.javerland.homecenter.metadata;

import org.jspecify.annotations.Nullable;

/** One episode from the Cinemeta series detail. */
record CinemetaEpisode(
        int season,
        int episode,
        @Nullable String title,
        @Nullable String description,
        @Nullable String thumbnailUrl,
        @Nullable Double rating) {
}
