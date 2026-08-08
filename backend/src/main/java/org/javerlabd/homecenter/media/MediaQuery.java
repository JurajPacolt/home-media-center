package org.javerlabd.homecenter.media;

import org.jspecify.annotations.Nullable;

/**
 * Filter for library listings. The limit is capped so one request cannot retrieve the entire index.
 *
 * @param sourceId narrows the listing to one source when several exist; {@code null} means all
 * @param genreId  internal movie genre ID; {@code null} means all
 */
public record MediaQuery(
        @Nullable MediaCategory category,
        @Nullable Long sourceId,
        @Nullable Long genreId,
        @Nullable String search,
        int limit,
        int offset) {

    public static final int DEFAULT_LIMIT = 60;
    public static final int MAX_LIMIT = 500;

    public MediaQuery {
        limit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        offset = Math.max(offset, 0);
        search = (search == null || search.isBlank()) ? null : search.trim();
    }

    public static MediaQuery of(@Nullable MediaCategory category) {
        return new MediaQuery(category, null, null, null, DEFAULT_LIMIT, 0);
    }

    public static MediaQuery ofSource(long sourceId) {
        return new MediaQuery(null, sourceId, null, null, DEFAULT_LIMIT, 0);
    }
}
