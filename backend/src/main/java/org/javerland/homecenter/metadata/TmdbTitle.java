package org.javerland.homecenter.metadata;

import java.util.List;

import org.javerland.homecenter.media.ProviderGenre;
import org.jspecify.annotations.Nullable;

record TmdbTitle(
        long id,
        String title,
        @Nullable String description,
        @Nullable String posterPath,
        @Nullable Integer year,
        @Nullable Double rating,
        List<ProviderGenre> genres,
        @Nullable TmdbCollection collection) {
}
