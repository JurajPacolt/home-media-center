package org.javerland.homecenter.metadata;

import java.util.Optional;

/**
 * Source of movie and series metadata. Implementations are ordered Spring beans; the scan
 * picks the first {@link #enabled()} one and keeps it for the whole run, so a single index
 * never mixes data from two providers.
 *
 * <p>{@link #name()} is written into {@code media_item.metadata_provider} and
 * {@code media_genre.provider}, therefore it must stay stable — changing it orphans every
 * previously indexed row.
 */
interface MetadataProvider {

    /** Identifier stored in the index, for example {@code TMDB} or {@code CINEMETA}. */
    String name();

    /** Whether the provider is configured and may be used. */
    boolean enabled();

    Optional<ResolvedVideoMetadata> resolve(ParsedVideoName parsed, MetadataSession session);

    /**
     * Downloads a poster referenced by {@link ResolvedVideoMetadata#remotePosterPath()}. The
     * value is provider specific — TMDb returns a relative path, Cinemeta an absolute URL.
     */
    byte[] downloadPoster(String remotePosterPath);
}
