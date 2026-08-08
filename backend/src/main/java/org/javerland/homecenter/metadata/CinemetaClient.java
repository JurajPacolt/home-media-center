package org.javerland.homecenter.metadata;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.javerland.homecenter.config.HomeCenterProperties;
import org.javerland.homecenter.media.ProviderGenre;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Client for the public Cinemeta catalogue (the Stremio add-on backing IMDb identifiers). It
 * needs no account and no token, which is exactly why it is the fallback when TMDb is not
 * configured. In exchange it only provides English texts and knows nothing about collections.
 */
@Component
class CinemetaClient {

    private static final String MOVIE = "movie";
    private static final String SERIES = "series";
    /** Cinemeta identifies everything by an IMDb id; anything else would be a foreign URL. */
    private static final String IMDB_ID = "tt\\d{4,}";

    private final HomeCenterProperties.Metadata properties;
    private final RestClient api;
    private final RestClient images;
    private final RequestThrottle throttle;

    CinemetaClient(HomeCenterProperties properties) {
        this.properties = properties.metadata();
        this.api = RestClient.builder()
                .baseUrl(this.properties.cinemetaBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.images = RestClient.builder().build();
        this.throttle = new RequestThrottle("Cinemeta", this.properties.requestDelay());
    }

    boolean enabled() {
        return properties.cinemetaFallback();
    }

    Optional<CinemetaTitle> movie(String query, @Nullable Integer year) {
        return search(MOVIE, query, year).flatMap(id -> meta(MOVIE, id));
    }

    Optional<CinemetaTitle> series(String query, @Nullable Integer year) {
        return search(SERIES, query, year).flatMap(id -> meta(SERIES, id));
    }

    private Optional<String> search(String type, String query, @Nullable Integer year) {
        String term = searchTerm(query);
        if (term.isEmpty()) {
            return Optional.empty();
        }
        CatalogResponse response = call(client -> client.get()
                .uri(builder -> builder.path("/catalog/{type}/top/search={query}.json").build(type, term))
                .retrieve()
                .body(CatalogResponse.class));
        List<CatalogItem> results = response.metas() == null ? List.of() : response.metas();
        return results.stream()
                .filter(item -> item.id() != null && item.id().matches(IMDB_ID))
                // Cinemeta cannot filter by year, so the year from the filename only reorders
                // the results. A tolerance of one year covers the usual gap between the
                // release and the local release the filename was named after.
                .filter(item -> year == null || matchesYear(item.releaseInfo(), year))
                .findFirst()
                .or(() -> results.stream()
                        .filter(item -> item.id() != null && item.id().matches(IMDB_ID))
                        .findFirst())
                .map(CatalogItem::id);
    }

    private Optional<CinemetaTitle> meta(String type, String imdbId) {
        if (!imdbId.matches(IMDB_ID)) {
            return Optional.empty();
        }
        MetaResponse response = call(client -> client.get()
                .uri(builder -> builder.path("/meta/{type}/{id}.json").build(type, imdbId))
                .retrieve()
                .body(MetaResponse.class));
        MetaDetail meta = response.meta();
        if (meta == null || meta.id() == null || !meta.id().matches(IMDB_ID) || blank(meta.name())) {
            return Optional.empty();
        }
        return Optional.of(new CinemetaTitle(meta.id(), meta.name(), meta.description(),
                blank(meta.poster()) ? null : meta.poster(),
                yearOf(meta.year() == null ? meta.releaseInfo() : meta.year()),
                rating(meta.imdbRating()), genres(meta.genres()), episodes(meta.videos())));
    }

    byte[] downloadPoster(String url) {
        throttle.await();
        byte[] body = images.get()
                .uri(posterUri(url))
                .accept(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG)
                .retrieve()
                .body(byte[].class);
        if (body == null || body.length == 0) {
            throw new RestClientException("Cinemeta vrátila prázdny plagát");
        }
        return body;
    }

    /**
     * Cinemeta returns absolute image URLs, so unlike TMDb the host is not fixed by
     * configuration. Only the known image CDN is allowed — a poster address from the response
     * must never be able to point the server at an arbitrary machine.
     */
    static URI posterUri(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Neplatná adresa plagátu z Cinemety");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = "https".equalsIgnoreCase(uri.getScheme())
                && (host.equals("metahub.space") || host.endsWith(".metahub.space"));
        if (!allowed) {
            throw new IllegalArgumentException("Neplatná adresa plagátu z Cinemety");
        }
        return uri;
    }

    /**
     * Cinemeta has no numeric genre identifiers, only names, while the index keys genres by
     * {@code (provider, provider_id)}. A stable hash of the name therefore stands in for the id
     * — the same name always yields the same row.
     */
    static long genreId(String name) {
        long hash = 0xcbf29ce484222325L;
        for (char character : name.toLowerCase(Locale.ROOT).toCharArray()) {
            hash ^= character;
            hash *= 0x100000001b3L;
        }
        return hash & Long.MAX_VALUE;
    }

    static String searchTerm(String query) {
        return query.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
    }

    private <T> T call(Function<RestClient, @Nullable T> request) {
        throttle.await();
        T body = request.apply(api);
        if (body == null) {
            throw new RestClientException("Cinemeta vrátila prázdnu odpoveď");
        }
        return body;
    }

    private static List<CinemetaEpisode> episodes(@Nullable List<VideoDetail> videos) {
        if (videos == null) {
            return List.of();
        }
        return videos.stream()
                .filter(video -> video.season() != null && video.episode() != null)
                .map(video -> new CinemetaEpisode(video.season(), video.episode(),
                        blank(video.name()) ? null : video.name(),
                        blank(video.description()) ? video.overview() : video.description(),
                        blank(video.thumbnail()) ? null : video.thumbnail(),
                        rating(video.rating())))
                .toList();
    }

    private static List<ProviderGenre> genres(@Nullable List<String> names) {
        if (names == null) {
            return List.of();
        }
        return names.stream()
                .filter(name -> !blank(name))
                .map(String::trim)
                .distinct()
                .map(name -> new ProviderGenre(genreId(name), name))
                .toList();
    }

    private static boolean matchesYear(@Nullable String releaseInfo, int year) {
        Integer found = yearOf(releaseInfo);
        return found != null && Math.abs(found - year) <= 1;
    }

    /** {@code releaseInfo} is {@code 1999} for a movie and {@code 2016–2025} for a series. */
    private static @Nullable Integer yearOf(@Nullable String value) {
        if (value == null || value.length() < 4) {
            return null;
        }
        try {
            return Integer.valueOf(value.substring(0, 4));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static @Nullable Double rating(@Nullable String value) {
        if (blank(value)) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return parsed <= 0 ? null : Math.round(parsed * 10.0) / 10.0;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CatalogResponse(@Nullable List<CatalogItem> metas) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CatalogItem(@Nullable String id, @Nullable String name, @Nullable String releaseInfo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MetaResponse(@Nullable MetaDetail meta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MetaDetail(
            @Nullable String id,
            @Nullable String name,
            @Nullable String description,
            @Nullable String poster,
            @Nullable String year,
            @Nullable String releaseInfo,
            // Ratings arrive as strings ("8.6"), but some entries send a number; String is the
            // only shape Jackson accepts for both without a custom deserializer.
            @Nullable String imdbRating,
            @Nullable List<String> genres,
            @Nullable List<VideoDetail> videos) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VideoDetail(
            @Nullable Integer season,
            @Nullable Integer episode,
            @Nullable String name,
            @Nullable String description,
            @Nullable String overview,
            @Nullable String thumbnail,
            @Nullable String rating) {
    }
}
