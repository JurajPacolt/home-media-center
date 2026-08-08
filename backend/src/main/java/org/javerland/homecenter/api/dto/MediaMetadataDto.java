package org.javerland.homecenter.api.dto;

import java.util.List;

import org.javerland.homecenter.media.MediaItem;
import org.javerland.homecenter.media.MediaMetadata;
import org.javerland.homecenter.media.VideoKind;
import org.jspecify.annotations.Nullable;

/** Movie data and ordering required by the Android client to group episodes. */
public record MediaMetadataDto(
        @Nullable VideoKind kind,
        @Nullable String description,
        @Nullable Integer releaseYear,
        @Nullable Double rating,
        @Nullable String groupKey,
        @Nullable String groupTitle,
        @Nullable Integer seasonNumber,
        @Nullable Integer episodeNumber,
        @Nullable Integer partNumber,
        List<MediaGenreDto> genres,
        @Nullable String posterUrl,
        @Nullable String provider,
        @Nullable Long providerId) {

    public static @Nullable MediaMetadataDto from(MediaItem item) {
        MediaMetadata metadata = item.metadata();
        if (metadata == null || (metadata.kind() == null && !metadata.matched())) {
            return null;
        }
        return new MediaMetadataDto(
                metadata.kind(), metadata.description(), metadata.releaseYear(), metadata.rating(),
                metadata.groupKey(), metadata.groupTitle(), metadata.seasonNumber(),
                metadata.episodeNumber(), metadata.partNumber(),
                metadata.genres().stream().map(MediaGenreDto::from).toList(),
                metadata.hasPoster() ? "/api/v1/media/" + item.requireId() + "/poster" : null,
                metadata.provider(), metadata.providerId());
    }
}
