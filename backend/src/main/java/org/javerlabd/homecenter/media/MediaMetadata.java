package org.javerlabd.homecenter.media;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/** Filmové údaje pripojené k indexovanému videosúboru. */
public record MediaMetadata(
        MetadataStatus status,
        @Nullable VideoKind kind,
        @Nullable String provider,
        @Nullable Long providerId,
        @Nullable String description,
        @Nullable String posterFile,
        @Nullable Integer releaseYear,
        @Nullable Double rating,
        @Nullable String groupKey,
        @Nullable String groupTitle,
        @Nullable Integer seasonNumber,
        @Nullable Integer episodeNumber,
        @Nullable Integer partNumber,
        List<MediaGenre> genres,
        @Nullable Instant updatedAt) {

    public MediaMetadata {
        genres = genres == null ? List.of() : List.copyOf(genres);
    }

    public boolean matched() {
        return status == MetadataStatus.MATCHED;
    }

    /** Providerové údaje môžu zostať použiteľné aj po neúspešnom obnovení. */
    public boolean hasProviderData() {
        return provider != null && !provider.isBlank() && providerId != null;
    }

    public boolean hasPoster() {
        return posterFile != null && !posterFile.isBlank();
    }

    public String genreNames() {
        return genres.stream().map(MediaGenre::name).collect(Collectors.joining(", "));
    }

    public MediaMetadata withGenres(List<MediaGenre> loadedGenres) {
        return new MediaMetadata(status, kind, provider, providerId, description, posterFile,
                releaseYear, rating, groupKey, groupTitle, seasonNumber, episodeNumber,
                partNumber, loadedGenres, updatedAt);
    }
}
