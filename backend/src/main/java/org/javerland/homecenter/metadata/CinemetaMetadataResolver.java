package org.javerland.homecenter.metadata;

import java.util.Optional;

import org.javerland.homecenter.media.VideoKind;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Token-free fallback provider. It resolves the same shape of metadata as TMDb, with two
 * documented limitations: texts are English only, and Cinemeta knows no collections, so
 * sequels are grouped only when the filename itself says which part it is.
 */
@Component
@Order(20)
class CinemetaMetadataResolver implements MetadataProvider {

    static final String PROVIDER = "CINEMETA";

    private final CinemetaClient client;

    CinemetaMetadataResolver(CinemetaClient client) {
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
        MovieKey key = new MovieKey(parsed.queryTitle(), parsed.year());
        Optional<CinemetaTitle> result = session.cached(key, ignored ->
                client.movie(parsed.queryTitle(), parsed.year()));
        return result.map(movie -> {
            String groupKey = null;
            String groupTitle = null;
            String title = movie.title();
            if (parsed.partNumber() != null) {
                groupKey = "cinemeta:movie:" + movie.imdbId();
                groupTitle = movie.title();
                title = movie.title() + " · časť " + parsed.partNumber();
            }
            return new ResolvedVideoMetadata(
                    title, VideoKind.MOVIE, movie.numericId(), movie.description(), movie.posterUrl(),
                    "cinemeta-movie-" + movie.numericId(), movie.year(), movie.rating(),
                    groupKey, groupTitle, null, null, parsed.partNumber(), movie.genres());
        });
    }

    private Optional<ResolvedVideoMetadata> resolveEpisode(ParsedVideoName parsed, MetadataSession session) {
        SeriesKey key = new SeriesKey(parsed.queryTitle(), parsed.year());
        Optional<CinemetaTitle> seriesResult = session.cached(key, ignored ->
                client.series(parsed.queryTitle(), parsed.year()));
        if (seriesResult.isEmpty()) {
            return Optional.empty();
        }
        CinemetaTitle series = seriesResult.get();
        int season = parsed.seasonNumber();
        int episodeNumber = parsed.episodeNumber();
        // The series detail already carries every episode, so no further request is needed.
        CinemetaEpisode episode = series.episode(season, episodeNumber);

        String code = "S%02dE%02d".formatted(season, episodeNumber);
        String title = episode == null || blank(episode.title())
                ? code : code + " · " + episode.title();
        String description = episode == null || blank(episode.description())
                ? series.description() : episode.description();
        String poster = episode == null || blank(episode.thumbnailUrl())
                ? series.posterUrl() : episode.thumbnailUrl();
        Double rating = episode == null || episode.rating() == null
                ? series.rating() : episode.rating();

        return Optional.of(new ResolvedVideoMetadata(
                title, VideoKind.TV_EPISODE, series.numericId(), description, poster,
                "cinemeta-tv-%d-s%02de%03d".formatted(series.numericId(), season, episodeNumber),
                series.year(), rating, "cinemeta:tv:" + series.imdbId(), series.title(),
                season, episodeNumber, parsed.partNumber(), series.genres()));
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private record MovieKey(String title, Integer year) {
    }

    private record SeriesKey(String title, Integer year) {
    }
}
