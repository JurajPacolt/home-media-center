package org.javerlabd.homecenter.api.dto;

import java.time.Instant;

import org.javerlabd.homecenter.media.MediaCategory;
import org.javerlabd.homecenter.media.MediaItem;
import org.jspecify.annotations.Nullable;

/** Položka knižnice tak, ako ju vidí Android TV klient. */
public record MediaItemDto(
        long id,
        /** Z ktorého Samba zdroja položka pochádza; zdrojov môže byť nastavených viac. */
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
        /** Adresa na prehrávanie; podporuje Range requesty. */
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
