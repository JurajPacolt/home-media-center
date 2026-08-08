package org.javerland.homecenter.metadata;

import org.jspecify.annotations.Nullable;

record TmdbEpisode(
        String title,
        @Nullable String description,
        @Nullable String stillPath,
        @Nullable Double rating) {
}
