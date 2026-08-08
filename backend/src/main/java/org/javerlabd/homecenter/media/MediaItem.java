package org.javerlabd.homecenter.media;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/** One index entry. It is created by a scan, and the REST API reads exclusively from here. */
public record MediaItem(
        @Nullable Long id,
        long sourceId,
        MediaCategory category,
        String relativePath,
        String fileName,
        String title,
        String extension,
        long sizeBytes,
        @Nullable Instant modifiedAt,
        String contentType,
        @Nullable MediaMetadata metadata,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt) {

    public long requireId() {
        if (id == null) {
            throw new IllegalStateException("Položka ešte nebola uložená");
        }
        return id;
    }

    public MediaItem withMetadata(@Nullable MediaMetadata loadedMetadata) {
        return new MediaItem(id, sourceId, category, relativePath, fileName, title, extension,
                sizeBytes, modifiedAt, contentType, loadedMetadata, createdAt, updatedAt);
    }
}
