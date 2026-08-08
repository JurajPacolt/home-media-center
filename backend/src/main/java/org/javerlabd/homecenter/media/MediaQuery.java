package org.javerlabd.homecenter.media;

import org.jspecify.annotations.Nullable;

/**
 * Filter pre výpis knižnice. Limit je zastropovaný, aby jeden request nevytiahol celý index.
 *
 * @param sourceId keď je zdrojov viac, dá sa výpis zúžiť na jeden; {@code null} = všetky
 * @param genreId  interné id filmového žánru; {@code null} = všetky
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
