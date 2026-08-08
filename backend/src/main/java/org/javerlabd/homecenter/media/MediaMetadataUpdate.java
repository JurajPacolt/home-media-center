package org.javerlabd.homecenter.media;

import java.util.List;

import org.jspecify.annotations.Nullable;

/** Úspešne rozpoznané video pripravené na zápis do indexu. */
public record MediaMetadataUpdate(
        String title,
        VideoKind kind,
        String provider,
        long providerId,
        @Nullable String description,
        @Nullable String posterFile,
        @Nullable Integer releaseYear,
        @Nullable Double rating,
        @Nullable String groupKey,
        @Nullable String groupTitle,
        @Nullable Integer seasonNumber,
        @Nullable Integer episodeNumber,
        @Nullable Integer partNumber,
        List<ProviderGenre> genres) {

    public MediaMetadataUpdate {
        genres = genres == null ? List.of() : List.copyOf(genres);
    }
}
