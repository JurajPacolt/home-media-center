package org.javerlabd.homecenter.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.javerlabd.homecenter.media.ProviderGenre;
import org.javerlabd.homecenter.media.VideoKind;
import org.junit.jupiter.api.Test;

class TmdbMetadataResolverTest {

    private final TmdbClient client = mock(TmdbClient.class);
    private final TmdbMetadataResolver resolver = new TmdbMetadataResolver(client);

    @Test
    void viacEpizodJednehoSerialuVyhladaSerialIbaRaz() {
        TmdbTitle series = new TmdbTitle(70523L, "Dark", "Stratené deti a časové slučky.",
                "/dark.jpg", 2017, 8.4, List.of(new ProviderGenre(18, "Dráma")), null);
        when(client.searchSeries("Dark", null)).thenReturn(Optional.of(70523L));
        when(client.series(70523L)).thenReturn(series);
        when(client.episode(70523L, 1, 1))
                .thenReturn(Optional.of(new TmdbEpisode("Tajomstvá", "Prvá epizóda", "/e1.jpg", 8.1)));
        when(client.episode(70523L, 1, 2))
                .thenReturn(Optional.of(new TmdbEpisode("Klamstvá", "Druhá epizóda", null, 8.2)));
        TmdbMetadataResolver.Session session = resolver.newSession();

        ResolvedVideoMetadata first = resolver.resolve(
                new ParsedVideoName("Dark", null, 1, 1, null), session).orElseThrow();
        ResolvedVideoMetadata second = resolver.resolve(
                new ParsedVideoName("Dark", null, 1, 2, null), session).orElseThrow();

        assertThat(first.kind()).isEqualTo(VideoKind.TV_EPISODE);
        assertThat(first.title()).isEqualTo("S01E01 · Tajomstvá");
        assertThat(first.groupKey()).isEqualTo("tmdb:tv:70523");
        assertThat(first.groupTitle()).isEqualTo("Dark");
        assertThat(first.remotePosterPath()).isEqualTo("/e1.jpg");
        assertThat(second.title()).isEqualTo("S01E02 · Klamstvá");
        assertThat(second.remotePosterPath()).isEqualTo("/dark.jpg");
        verify(client, times(1)).searchSeries("Dark", null);
        verify(client, times(1)).series(70523L);
    }

    @Test
    void filmyZKolekcieDostanuSpolocnyKluc() {
        TmdbTitle movie = new TmdbTitle(603L, "Matrix", "Simulovaný svet.",
                "/matrix.jpg", 1999, 8.2, List.of(new ProviderGenre(878, "Sci-fi")),
                new TmdbCollection(2344L, "Kolekcia Matrix"));
        when(client.searchMovie("Matrix", 1999)).thenReturn(Optional.of(603L));
        when(client.movie(603L)).thenReturn(movie);

        ResolvedVideoMetadata result = resolver.resolve(
                new ParsedVideoName("Matrix", 1999, null, null, 2), resolver.newSession())
                .orElseThrow();

        assertThat(result.kind()).isEqualTo(VideoKind.MOVIE);
        assertThat(result.groupKey()).isEqualTo("tmdb:collection:2344");
        assertThat(result.groupTitle()).isEqualTo("Kolekcia Matrix");
        assertThat(result.partNumber()).isEqualTo(2);
        assertThat(result.genres()).extracting(ProviderGenre::name).containsExactly("Sci-fi");
    }
}
