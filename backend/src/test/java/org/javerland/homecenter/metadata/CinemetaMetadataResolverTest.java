package org.javerland.homecenter.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.javerland.homecenter.media.ProviderGenre;
import org.javerland.homecenter.media.VideoKind;
import org.junit.jupiter.api.Test;

class CinemetaMetadataResolverTest {

    private final CinemetaClient client = mock(CinemetaClient.class);
    private final CinemetaMetadataResolver resolver = new CinemetaMetadataResolver(client);

    @Test
    void viacEpizodJednehoSerialuVyhladaSerialIbaRaz() {
        CinemetaTitle series = new CinemetaTitle("tt4574334", "Stranger Things", "Missing children.",
                "https://images.metahub.space/poster/small/tt4574334/img", 2016, 8.6,
                List.of(new ProviderGenre(CinemetaClient.genreId("Drama"), "Drama")),
                List.of(new CinemetaEpisode(1, 1, "Chapter One", "The vanishing.",
                                "https://episodes.metahub.space/tt4574334/1/1/w780.jpg", 8.1),
                        new CinemetaEpisode(1, 2, "Chapter Two", null, null, null)));
        when(client.series("Stranger Things", null)).thenReturn(Optional.of(series));
        MetadataSession session = new MetadataSession(resolver);

        ResolvedVideoMetadata first = resolver.resolve(
                new ParsedVideoName("Stranger Things", null, 1, 1, null), session).orElseThrow();
        ResolvedVideoMetadata second = resolver.resolve(
                new ParsedVideoName("Stranger Things", null, 1, 2, null), session).orElseThrow();

        assertThat(first.kind()).isEqualTo(VideoKind.TV_EPISODE);
        assertThat(first.title()).isEqualTo("S01E01 · Chapter One");
        assertThat(first.providerId()).isEqualTo(4_574_334L);
        assertThat(first.groupKey()).isEqualTo("cinemeta:tv:tt4574334");
        assertThat(first.groupTitle()).isEqualTo("Stranger Things");
        assertThat(first.remotePosterPath()).isEqualTo("https://episodes.metahub.space/tt4574334/1/1/w780.jpg");
        assertThat(first.posterCacheKey()).isEqualTo("cinemeta-tv-4574334-s01e001");
        // The episode has no text of its own, so the series description and poster stand in.
        assertThat(second.title()).isEqualTo("S01E02 · Chapter Two");
        assertThat(second.description()).isEqualTo("Missing children.");
        assertThat(second.remotePosterPath()).isEqualTo("https://images.metahub.space/poster/small/tt4574334/img");
        assertThat(second.rating()).isEqualTo(8.6);
        verify(client, times(1)).series("Stranger Things", null);
    }

    @Test
    void neznamaEpizodaPouzijeUdajeSerialu() {
        CinemetaTitle series = new CinemetaTitle("tt4574334", "Stranger Things", "Missing children.",
                null, 2016, 8.6, List.of(), List.of());
        when(client.series("Stranger Things", null)).thenReturn(Optional.of(series));

        ResolvedVideoMetadata resolved = resolver.resolve(
                new ParsedVideoName("Stranger Things", null, 9, 9, null),
                new MetadataSession(resolver)).orElseThrow();

        assertThat(resolved.title()).isEqualTo("S09E09");
        assertThat(resolved.description()).isEqualTo("Missing children.");
        assertThat(resolved.remotePosterPath()).isNull();
    }

    @Test
    void filmSCastouDostaneSkupinovyKluc() {
        CinemetaTitle movie = new CinemetaTitle("tt0133093", "The Matrix", "A simulated world.",
                "https://images.metahub.space/poster/small/tt0133093/img", 1999, 8.7,
                List.of(new ProviderGenre(CinemetaClient.genreId("Sci-Fi"), "Sci-Fi")), List.of());
        when(client.movie("Matrix", 1999)).thenReturn(Optional.of(movie));

        ResolvedVideoMetadata resolved = resolver.resolve(
                new ParsedVideoName("Matrix", 1999, null, null, 2),
                new MetadataSession(resolver)).orElseThrow();

        assertThat(resolved.kind()).isEqualTo(VideoKind.MOVIE);
        assertThat(resolved.title()).isEqualTo("The Matrix · časť 2");
        assertThat(resolved.groupKey()).isEqualTo("cinemeta:movie:tt0133093");
        assertThat(resolved.partNumber()).isEqualTo(2);
        assertThat(resolved.posterCacheKey()).isEqualTo("cinemeta-movie-133093");
        assertThat(resolved.genres()).extracting(ProviderGenre::name).containsExactly("Sci-Fi");
    }

    @Test
    void nenajdenyFilmVratiPrazdnyVysledok() {
        when(client.movie("Neznámy", null)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(new ParsedVideoName("Neznámy", null, null, null, null),
                new MetadataSession(resolver))).isEmpty();
    }

    @Test
    void metadataSaUloziaPodProviderom() {
        CinemetaTitle movie = new CinemetaTitle("tt0133093", "The Matrix", null, null, 1999, null,
                List.of(), List.of());
        when(client.movie("Matrix", null)).thenReturn(Optional.of(movie));

        ResolvedVideoMetadata resolved = resolver.resolve(
                new ParsedVideoName("Matrix", null, null, null, null),
                new MetadataSession(resolver)).orElseThrow();

        assertThat(resolved.toUpdate(resolver.name(), null).provider()).isEqualTo("CINEMETA");
    }
}
