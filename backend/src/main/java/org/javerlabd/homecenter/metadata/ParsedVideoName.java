package org.javerlabd.homecenter.metadata;

import org.javerlabd.homecenter.media.MediaStructure;
import org.javerlabd.homecenter.media.VideoKind;
import org.jspecify.annotations.Nullable;

/** Information safely derived from the name and path without opening the video file. */
public record ParsedVideoName(
        String queryTitle,
        @Nullable Integer year,
        @Nullable Integer seasonNumber,
        @Nullable Integer episodeNumber,
        @Nullable Integer partNumber) {

    public boolean episode() {
        return seasonNumber != null && episodeNumber != null;
    }

    public MediaStructure structure() {
        if (episode()) {
            return new MediaStructure(VideoKind.TV_EPISODE,
                    localKey("series", queryTitle), queryTitle,
                    seasonNumber, episodeNumber, partNumber);
        }
        if (partNumber != null) {
            return new MediaStructure(VideoKind.MOVIE,
                    localKey("parts", queryTitle), queryTitle,
                    null, null, partNumber);
        }
        return new MediaStructure(VideoKind.MOVIE, null, null, null, null, null);
    }

    private static String localKey(String type, String title) {
        String normalized = title.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (normalized.length() > 220) {
            normalized = normalized.substring(0, 220);
        }
        return "local:" + type + ":" + normalized;
    }
}
