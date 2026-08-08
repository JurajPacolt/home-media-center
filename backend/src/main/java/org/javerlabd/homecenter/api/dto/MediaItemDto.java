package org.javerlabd.homecenter.api.dto;

import java.time.Instant;

import org.javerlabd.homecenter.media.MediaCategory;
import org.javerlabd.homecenter.media.MediaItem;
import org.jspecify.annotations.Nullable;

/** A library item as seen by the Android TV client. */
public record MediaItemDto(
        long id,
        /** The Samba source this item comes from; multiple sources may be configured. */
        long sourceId,
        MediaCategory category,
        String title,
        String fileName,
        String relativePath,
        String extension,
        long sizeBytes,
        @Nullable Instant modifiedAt,
        String contentType,
        @Nullable MediaMetadataDto metadata,
        /** Playback address with Range request support. */
        String streamUrl) {

    public static MediaItemDto from(MediaItem item) {
        long id = item.requireId();
        return new MediaItemDto(
                id,
                item.sourceId(),
                item.category(),
                item.title(),
                item.fileName(),
                item.relativePath(),
                item.extension(),
                item.sizeBytes(),
                item.modifiedAt(),
                item.contentType(),
                MediaMetadataDto.from(item),
                "/api/v1/media/" + id + "/stream");
    }
}
