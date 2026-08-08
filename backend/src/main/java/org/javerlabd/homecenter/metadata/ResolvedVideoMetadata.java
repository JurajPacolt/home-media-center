package org.javerlabd.homecenter.metadata;

import java.util.List;

import org.javerlabd.homecenter.media.MediaMetadataUpdate;
import org.javerlabd.homecenter.media.ProviderGenre;
import org.javerlabd.homecenter.media.VideoKind;
import org.jspecify.annotations.Nullable;

record ResolvedVideoMetadata(
        String title,
        VideoKind kind,
        long providerId,
        @Nullable String description,
        @Nullable String remotePosterPath,
        String posterCacheKey,
        @Nullable Integer releaseYear,
        @Nullable Double rating,
        @Nullable String groupKey,
        @Nullable String groupTitle,
        @Nullable Integer seasonNumber,
        @Nullable Integer episodeNumber,
        @Nullable Integer partNumber,
        List<ProviderGenre> genres) {

    MediaMetadataUpdate toUpdate(@Nullable String posterFile) {
        return new MediaMetadataUpdate(title, kind, "TMDB", providerId, description,
                posterFile, releaseYear, rating, groupKey, groupTitle,
                seasonNumber, episodeNumber, partNumber, genres);
    }
}
