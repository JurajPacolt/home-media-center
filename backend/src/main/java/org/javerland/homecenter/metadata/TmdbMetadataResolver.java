package org.javerland.homecenter.metadata;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.javerland.homecenter.media.VideoKind;
import org.springframework.stereotype.Component;

/** Combines title search, series/movie details, and a specific episode. */
@Component
public class TmdbMetadataResolver {

    private final TmdbClient client;

    public TmdbMetadataResolver(TmdbClient client) {
        this.client = client;
    }

    public Session newSession() {
        return new Session();
    }

    public Optional<ResolvedVideoMetadata> resolve(ParsedVideoName parsed, Session session) {
        if (!session.available) {
            return Optional.empty();
        }
        return parsed.episode() ? resolveEpisode(parsed, session) : resolveMovie(parsed, session);
    }

    private Optional<ResolvedVideoMetadata> resolveMovie(ParsedVideoName parsed, Session session) {
        SearchKey key = new SearchKey(parsed.queryTitle(), parsed.year());
        Optional<TmdbTitle> result = session.movies.computeIfAbsent(key, ignored ->
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

    private Optional<ResolvedVideoMetadata> resolveEpisode(ParsedVideoName parsed, Session session) {
        SearchKey key = new SearchKey(parsed.queryTitle(), parsed.year());
        Optional<TmdbTitle> seriesResult = session.series.computeIfAbsent(key, ignored ->
                client.searchSeries(parsed.queryTitle(), parsed.year()).map(client::series));
        if (seriesResult.isEmpty()) {
            return Optional.empty();
        }
        TmdbTitle series = seriesResult.get();
        int season = parsed.seasonNumber();
        int episodeNumber = parsed.episodeNumber();
        EpisodeKey episodeKey = new EpisodeKey(series.id(), season, episodeNumber);
        Optional<TmdbEpisode> episode = session.episodes.computeIfAbsent(episodeKey, ignored ->
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

    /** Cache valid only for one scan, avoiding a repeated series search for every episode. */
    public static final class Session {
        private final Map<SearchKey, Optional<TmdbTitle>> movies = new HashMap<>();
        private final Map<SearchKey, Optional<TmdbTitle>> series = new HashMap<>();
        private final Map<EpisodeKey, Optional<TmdbEpisode>> episodes = new HashMap<>();
        private boolean available = true;

        public void disable() {
            available = false;
        }
    }

    private record SearchKey(String title, Integer year) {
    }

    private record EpisodeKey(long seriesId, int season, int episode) {
    }
}
