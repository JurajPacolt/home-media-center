package org.javerland.homecenter.metadata;

import java.util.Optional;

import org.javerland.homecenter.media.VideoKind;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Combines title search, series/movie details, and a specific episode. */
@Component
@Order(10)
public class TmdbMetadataResolver implements MetadataProvider {

    static final String PROVIDER = "TMDB";

    private final TmdbClient client;

    public TmdbMetadataResolver(TmdbClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return PROVIDER;
    }

    @Override
    public boolean enabled() {
        return client.enabled();
    }

    @Override
    public byte[] downloadPoster(String remotePosterPath) {
        return client.downloadPoster(remotePosterPath);
    }

    @Override
    public Optional<ResolvedVideoMetadata> resolve(ParsedVideoName parsed, MetadataSession session) {
        return parsed.episode() ? resolveEpisode(parsed, session) : resolveMovie(parsed, session);
    }

    private Optional<ResolvedVideoMetadata> resolveMovie(ParsedVideoName parsed, MetadataSession session) {
        SearchKey key = new SearchKey(parsed.queryTitle(), parsed.year());
        Optional<TmdbTitle> result = session.cached(key, ignored ->
                client.searchMovie(parsed.queryTitle(), parsed.year()).map(client::movie));
        return result.map(movie -> {
            String groupKey = null;
            String groupTitle = null;
            String title = movie.title();
            // A collection is more precise than a filename: for example, it links sequels
            // that each have their own movie ID but belong to one series.
            if (movie.collection() != null) {
                groupKey = "tmdb:collection:" + movie.collection().id();
                groupTitle = movie.collection().name();
            } else if (parsed.partNumber() != null) {
                groupKey = "tmdb:movie:" + movie.id();
                groupTitle = movie.title();
            }
            if (parsed.partNumber() != null) {
                title = movie.title() + " · časť " + parsed.partNumber();
            }
            return new ResolvedVideoMetadata(
                    title, VideoKind.MOVIE, movie.id(), movie.description(), movie.posterPath(),
                    "movie-" + movie.id(), movie.year(), movie.rating(), groupKey, groupTitle,
                    null, null, parsed.partNumber(), movie.genres());
        });
    }

    private Optional<ResolvedVideoMetadata> resolveEpisode(ParsedVideoName parsed, MetadataSession session) {
        SeriesKey key = new SeriesKey(parsed.queryTitle(), parsed.year());
        Optional<TmdbTitle> seriesResult = session.cached(key, ignored ->
                client.searchSeries(parsed.queryTitle(), parsed.year()).map(client::series));
        if (seriesResult.isEmpty()) {
            return Optional.empty();
        }
        TmdbTitle series = seriesResult.get();
        int season = parsed.seasonNumber();
        int episodeNumber = parsed.episodeNumber();
        EpisodeKey episodeKey = new EpisodeKey(series.id(), season, episodeNumber);
        Optional<TmdbEpisode> episode = session.cached(episodeKey, ignored ->
                client.episode(series.id(), season, episodeNumber));

        String code = "S%02dE%02d".formatted(season, episodeNumber);
        String title = episode.map(TmdbEpisode::title)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> code + " · " + value)
                .orElse(code);
        String description = episode.map(TmdbEpisode::description)
                .filter(value -> value != null && !value.isBlank())
                .orElse(series.description());
        String poster = episode.map(TmdbEpisode::stillPath)
                .filter(value -> value != null && !value.isBlank())
                .orElse(series.posterPath());
        Double rating = episode.map(TmdbEpisode::rating).orElse(series.rating());

        return Optional.of(new ResolvedVideoMetadata(
                title, VideoKind.TV_EPISODE, series.id(), description, poster,
                "tv-%d-s%02de%03d".formatted(series.id(), season, episodeNumber),
                series.year(), rating, "tmdb:tv:" + series.id(), series.title(),
                season, episodeNumber, parsed.partNumber(), series.genres()));
    }

    private record SearchKey(String title, Integer year) {
    }

    private record SeriesKey(String title, Integer year) {
    }

    private record EpisodeKey(long seriesId, int season, int episode) {
    }
}
