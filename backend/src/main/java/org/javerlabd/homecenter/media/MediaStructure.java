package org.javerlabd.homecenter.media;

import org.jspecify.annotations.Nullable;

/** Poradie a lokálne zoskupenie rozpoznateľné už zo súborového názvu. */
public record MediaStructure(
        VideoKind kind,
        @Nullable String groupKey,
        @Nullable String groupTitle,
        @Nullable Integer seasonNumber,
        @Nullable Integer episodeNumber,
        @Nullable Integer partNumber) {
}
