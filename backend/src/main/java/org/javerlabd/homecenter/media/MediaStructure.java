package org.javerlabd.homecenter.media;

import org.jspecify.annotations.Nullable;

/** Ordering and local grouping that can be derived from the filename alone. */
public record MediaStructure(
        VideoKind kind,
        @Nullable String groupKey,
        @Nullable String groupTitle,
        @Nullable Integer seasonNumber,
        @Nullable Integer episodeNumber,
        @Nullable Integer partNumber) {
}
