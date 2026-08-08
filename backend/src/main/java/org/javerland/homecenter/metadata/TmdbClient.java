package org.javerland.homecenter.metadata;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.javerland.homecenter.config.HomeCenterProperties;
import org.javerland.homecenter.media.ProviderGenre;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Thin client for the official TMDb API v3. The token is never logged. */
@Component
public class TmdbClient {

    private final HomeCenterProperties.Metadata properties;
    private final @Nullable RestClient api;
    private final @Nullable RestClient images;
    private final Object throttleMonitor = new Object();
    private long nextRequestNanos;

    public TmdbClient(HomeCenterProperties properties) {
        this.properties = properties.metadata();
        if (this.properties.enabled()) {
            this.api = RestClient.builder()
                    .baseUrl(this.properties.apiBaseUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION,
                            "Bearer " + this.properties.tmdbReadAccessToken().trim())
                    .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .build();
            this.images = RestClient.builder().build();
        } else {
            this.api = null;
            this.images = null;
        }
    }

    public boolean enabled() {
        return api != null;
    }

    public Optional<Long> searchMovie(String query, @Nullable Integer year) {
        SearchResponse response = callApi(client -> client.get()
                .uri(builder -> {
                    builder.path("/search/movie")
                            .queryParam("query", query)
                            .queryParam("include_adult", false)
                            .queryParam("language", properties.language());
                    if (year != null) {
                        builder.queryParam("year", year);
                    }
                    return builder.build();
                })
                .retrieve()
                .body(SearchResponse.class));
        return firstId(response);
    }

    public Optional<Long> searchSeries(String query, @Nullable Integer year) {
        SearchResponse response = callApi(client -> client.get()
                .uri(builder -> {
                    builder.path("/search/tv")
                            .queryParam("query", query)
                            .queryParam("include_adult", false)
                            .queryParam("language", properties.language());
                    if (year != null) {
                        builder.queryParam("first_air_date_year", year);
                    }
                    return builder.build();
                })
                .retrieve()
                .body(SearchResponse.class));
        return firstId(response);
    }

    public TmdbTitle movie(long id) {
        MovieResponse primary = movie(id, properties.language());
        MovieResponse fallback = needsFallback(primary.overview()) ? movieFallback(id, primary) : primary;
        return new TmdbTitle(primary.id(), firstNonBlank(primary.title(), fallback.title()),
                firstNonBlank(primary.overview(), fallback.overview()),
                firstNonBlank(primary.posterPath(), fallback.posterPath()),
                yearOf(firstNonBlank(primary.releaseDate(), fallback.releaseDate())),
                usefulRating(primary.voteAverage()), genres(primary.genres(), fallback.genres()),
                primary.collection() == null ? collection(fallback.collection()) : collection(primary.collection()));
    }

    public TmdbTitle series(long id) {
        TvResponse primary = series(id, properties.language());
        TvResponse fallback = needsFallback(primary.overview()) ? seriesFallback(id, primary) : primary;
        return new TmdbTitle(primary.id(), firstNonBlank(primary.name(), fallback.name()),
                firstNonBlank(primary.overview(), fallback.overview()),
                firstNonBlank(primary.posterPath(), fallback.posterPath()),
                yearOf(firstNonBlank(primary.firstAirDate(), fallback.firstAirDate())),
                usefulRating(primary.voteAverage()), genres(primary.genres(), fallback.genres()), null);
    }

    public Optional<TmdbEpisode> episode(long seriesId, int season, int episode) {
        try {
            EpisodeResponse primary = episode(seriesId, season, episode, properties.language());
            EpisodeResponse fallback = needsFallback(primary.overview())
                    ? episodeFallback(seriesId, season, episode, primary) : primary;
            return Optional.of(new TmdbEpisode(
                    firstNonBlank(primary.name(), fallback.name()),
                    firstNonBlank(primary.overview(), fallback.overview()),
                    firstNonBlank(primary.stillPath(), fallback.stillPath()),
                    usefulRating(primary.voteAverage())));
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        }
    }

    public byte[] downloadPoster(String remotePath) {
        RestClient client = require(images);
        throttle();
        byte[] body = client.get()
                // Build the URI explicitly: during normal URI resolution, a path starting
                // with a slash would discard /t/p/w342 from imageBaseUrl.
                .uri(posterUri(properties.imageBaseUrl(), remotePath))
                .accept(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG)
                .retrieve()
                .body(byte[].class);
        if (body == null || body.length == 0) {
            throw new RestClientException("TMDb vrátil prázdny plagát");
        }
        return body;
    }

    static URI posterUri(String imageBaseUrl, String remotePath) {
        if (remotePath == null || remotePath.isBlank()
                || !remotePath.matches("(?i)^/?[a-z0-9_-]+\\.(?:jpe?g|png|webp)$")) {
            throw new IllegalArgumentException("Neplatná cesta plagátu z TMDb");
        }
        String base = imageBaseUrl.replaceFirst("/+$", "");
        String fileName = remotePath.replaceFirst("^/+", "");
        return URI.create(base + "/" + fileName);
    }

    private MovieResponse movie(long id, String language) {
        return callApi(client -> client.get()
                .uri(builder -> builder.path("/movie/{id}").queryParam("language", language).build(id))
                .retrieve().body(MovieResponse.class));
    }

    private MovieResponse movieFallback(long id, MovieResponse primary) {
        if (properties.language().equalsIgnoreCase(properties.fallbackLanguage())) {
            return primary;
        }
        return movie(id, properties.fallbackLanguage());
    }

    private TvResponse series(long id, String language) {
        return callApi(client -> client.get()
                .uri(builder -> builder.path("/tv/{id}").queryParam("language", language).build(id))
                .retrieve().body(TvResponse.class));
    }

    private TvResponse seriesFallback(long id, TvResponse primary) {
        if (properties.language().equalsIgnoreCase(properties.fallbackLanguage())) {
            return primary;
        }
        return series(id, properties.fallbackLanguage());
    }

    private EpisodeResponse episode(long seriesId, int season, int episode, String language) {
        return callApi(client -> client.get()
                .uri(builder -> builder.path("/tv/{seriesId}/season/{season}/episode/{episode}")
                        .queryParam("language", language)
                        .build(seriesId, season, episode))
                .retrieve().body(EpisodeResponse.class));
    }

    private EpisodeResponse episodeFallback(long seriesId, int season, int episode,
                                             EpisodeResponse primary) {
        if (properties.language().equalsIgnoreCase(properties.fallbackLanguage())) {
            return primary;
        }
        return episode(seriesId, season, episode, properties.fallbackLanguage());
    }

    private <T> T callApi(java.util.function.Function<RestClient, @Nullable T> request) {
        throttle();
        T body = request.apply(require(api));
        if (body == null) {
            throw new RestClientException("TMDb vrátil prázdnu odpoveď");
        }
        return body;
    }

    private void throttle() {
        Duration delay = properties.requestDelay();
        if (delay.isZero() || delay.isNegative()) {
            return;
        }
        synchronized (throttleMonitor) {
            long now = System.nanoTime();
            long remaining = nextRequestNanos - now;
            if (remaining > 0) {
                try {
                    Thread.sleep(Duration.ofNanos(remaining));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new RestClientException("Čakanie na TMDb bolo prerušené", ex);
                }
            }
            nextRequestNanos = System.nanoTime() + delay.toNanos();
        }
    }

    private static Optional<Long> firstId(@Nullable SearchResponse response) {
        if (response == null || response.results() == null || response.results().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(response.results().getFirst().id());
    }

    private static List<ProviderGenre> genres(@Nullable List<GenreResponse> primary,
                                              @Nullable List<GenreResponse> fallback) {
        List<GenreResponse> source = primary == null || primary.isEmpty() ? fallback : primary;
        if (source == null) {
            return List.of();
        }
        return source.stream()
                .filter(genre -> genre.name() != null && !genre.name().isBlank())
                .map(genre -> new ProviderGenre(genre.id(), genre.name()))
                .toList();
    }

    private static @Nullable TmdbCollection collection(@Nullable CollectionResponse value) {
        return value == null || value.name() == null
                ? null : new TmdbCollection(value.id(), value.name());
    }

    private static @Nullable Integer yearOf(@Nullable String date) {
        if (date == null || date.length() < 4) {
            return null;
        }
        try {
            return Integer.valueOf(date.substring(0, 4));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static @Nullable Double usefulRating(double value) {
        return value <= 0 ? null : Math.round(value * 10.0) / 10.0;
    }

    private static boolean needsFallback(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static @Nullable String firstNonBlank(@Nullable String primary, @Nullable String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private static RestClient require(@Nullable RestClient client) {
        if (client == null) {
            throw new IllegalStateException("TMDb nie je nakonfigurované");
        }
        return client;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(@Nullable List<SearchResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResult(long id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GenreResponse(long id, @Nullable String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CollectionResponse(long id, @Nullable String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MovieResponse(
            long id,
            @Nullable String title,
            @Nullable String overview,
            @JsonProperty("poster_path") @Nullable String posterPath,
            @JsonProperty("release_date") @Nullable String releaseDate,
            @JsonProperty("vote_average") double voteAverage,
            @Nullable List<GenreResponse> genres,
            @JsonProperty("belongs_to_collection") @Nullable CollectionResponse collection) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TvResponse(
            long id,
            @Nullable String name,
            @Nullable String overview,
            @JsonProperty("poster_path") @Nullable String posterPath,
            @JsonProperty("first_air_date") @Nullable String firstAirDate,
            @JsonProperty("vote_average") double voteAverage,
            @Nullable List<GenreResponse> genres) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EpisodeResponse(
            @Nullable String name,
            @Nullable String overview,
            @JsonProperty("still_path") @Nullable String stillPath,
            @JsonProperty("vote_average") double voteAverage) {
    }
}
