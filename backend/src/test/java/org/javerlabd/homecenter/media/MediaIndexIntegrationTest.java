package org.javerlabd.homecenter.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.javerlabd.homecenter.metadata.MetadataEnrichmentService;
import org.javerlabd.homecenter.source.SmbSource;
import org.javerlabd.homecenter.source.SmbSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs against a real in-memory H2 instance, verifying that the Flyway migration succeeds
 * and generated keys are returned.
 */
@SpringBootTest
@ActiveProfiles("test")
class MediaIndexIntegrationTest {

    @Autowired
    private SmbSourceRepository sourceRepository;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private MetadataEnrichmentService metadataEnrichmentService;

    @Autowired
    private JdbcClient jdbc;

    /** The context and database are shared between tests, so each test starts clean. */
    @BeforeEach
    void clearIndex() {
        jdbc.sql("DELETE FROM media_item").update();
        jdbc.sql("DELETE FROM smb_source").update();
    }

    @Test
    void zdrojSaUloziAjSHeslomAVratiSaSId() {
        SmbSource saved = sourceRepository.save(newSource());

        assertThat(saved.id()).isNotNull();
        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.enabled()).isTrue();
        assertThat(sourceRepository.findById(saved.requireId()))
                .get()
                .satisfies(loaded -> assertThat(loaded.password()).isEqualTo("tajne"));
        assertThat(saved.toString()).doesNotContain("tajne");
    }

    @Test
    void casySaVratiaPresneAkoBoliUlozene() {
        SmbSource source = sourceRepository.save(newSource());
        Instant modified = Instant.parse("2026-01-01T10:00:00Z");
        mediaRepository.upsert(item(source.requireId(), "filmy/matrix.mkv", "matrix.mkv"), 1L);

        assertThat(mediaRepository.find(new MediaQuery(null, null, null, null, 10, 0)))
                .singleElement()
                .satisfies(item -> assertThat(item.modifiedAt()).isEqualTo(modified));
    }

    @Test
    void skenPridavaAktualizujeAUpratujePolozky() {
        SmbSource source = sourceRepository.save(newSource());
        long sourceId = source.requireId();

        assertThat(mediaRepository.upsert(item(sourceId, "filmy/matrix.mkv", "matrix.mkv"), 1L)).isTrue();
        assertThat(mediaRepository.upsert(item(sourceId, "filmy/matrix.mkv", "matrix.mkv"), 2L))
                .as("rovnaká cesta pri ďalšom skene je aktualizácia, nie nový záznam")
                .isFalse();

        assertThat(mediaRepository.count(MediaQuery.of(MediaCategory.VIDEO))).isEqualTo(1);
        assertThat(mediaRepository.countByCategory())
                .containsEntry(MediaCategory.VIDEO, 1L)
                .containsEntry(MediaCategory.PHOTO, 0L);
        assertThat(mediaRepository.totalSizeBytes()).isEqualTo(1024);

        // The third scan no longer found the file, so it must leave the index.
        assertThat(mediaRepository.deleteMissedBy(sourceId, 3L)).isEqualTo(1);
        assertThat(mediaRepository.count(new MediaQuery(null, null, null, null, 50, 0))).isZero();
    }

    @Test
    void hladanieBerieNazovAjCestu() {
        SmbSource source = sourceRepository.save(newSource());
        long sourceId = source.requireId();
        mediaRepository.upsert(item(sourceId, "filmy/akcia/matrix.mkv", "matrix.mkv"), 1L);
        mediaRepository.upsert(item(sourceId, "filmy/komedia/pulp.mkv", "pulp.mkv"), 1L);

        assertThat(mediaRepository.find(new MediaQuery(null, null, null, "matrix", 50, 0)))
                .extracting(MediaItem::fileName)
                .containsExactly("matrix.mkv");
        assertThat(mediaRepository.find(new MediaQuery(null, null, null, "komedia", 50, 0)))
                .extracting(MediaItem::fileName)
                .containsExactly("pulp.mkv");
    }

    @Test
    void vypisSaRadiBezOhladuNaVelkostPismen() {
        SmbSource source = sourceRepository.save(newSource());
        long sourceId = source.requireId();
        mediaRepository.upsert(item(sourceId, "zebra.mkv", "zebra.mkv"), 1L);
        mediaRepository.upsert(item(sourceId, "Avatar.mkv", "Avatar.mkv"), 1L);

        assertThat(mediaRepository.find(new MediaQuery(null, null, null, null, 50, 0)))
                .extracting(MediaItem::title)
                .containsExactly("Avatar", "zebra");
    }

    @Test
    void metadataUloziaZanreADajuSaPodlaNichFiltrovat() {
        SmbSource source = sourceRepository.save(newSource());
        long sourceId = source.requireId();
        mediaRepository.upsert(item(sourceId, "filmy/matrix.mkv", "matrix.mkv"), 1L);
        MediaItem saved = mediaRepository.findBySourceAndPath(sourceId, "filmy/matrix.mkv").orElseThrow();

        mediaRepository.saveMetadata(saved.requireId(), new MediaMetadataUpdate(
                "Matrix", VideoKind.MOVIE, "TMDB", 603L,
                "Programátor odhalí skutočnú povahu sveta.", "movie-603.jpg",
                1999, 8.2, "tmdb:collection:2344", "Kolekcia Matrix",
                null, null, null,
                List.of(new ProviderGenre(28, "Akčný"), new ProviderGenre(878, "Sci-fi"))));

        MediaItem loaded = mediaRepository.findById(saved.requireId()).orElseThrow();
        assertThat(loaded.title()).isEqualTo("Matrix");
        assertThat(loaded.metadata()).isNotNull();
        assertThat(loaded.metadata().genres()).extracting(MediaGenre::name)
                .containsExactly("Akčný", "Sci-fi");
        assertThat(mediaRepository.findGenres()).extracting(MediaGenre::name)
                .containsExactly("Akčný", "Sci-fi");

        long actionGenre = mediaRepository.findGenres().stream()
                .filter(genre -> genre.name().equals("Akčný"))
                .findFirst().orElseThrow().id();
        assertThat(mediaRepository.count(new MediaQuery(
                MediaCategory.VIDEO, null, actionGenre, null, 50, 0))).isEqualTo(1);

        // Another scan without a TMDb token must not replace the precise collection key
        // with a rough local estimate derived from the filename.
        metadataEnrichmentService.enrich(
                item(sourceId, "filmy/matrix.mkv", "matrix.mkv"),
                metadataEnrichmentService.newSession());
        assertThat(mediaRepository.findById(saved.requireId()).orElseThrow().metadata().groupKey())
                .isEqualTo("tmdb:collection:2344");
    }

    @Test
    void epizodyVSkupineSaRadiaPodlaCislaNieAbecedy() {
        SmbSource source = sourceRepository.save(newSource());
        long sourceId = source.requireId();
        mediaRepository.upsert(item(sourceId, "show/show.S01E10.mkv", "show.S01E10.mkv"), 1L);
        mediaRepository.upsert(item(sourceId, "show/show.S01E02.mkv", "show.S01E02.mkv"), 1L);
        MediaItem episode10 = mediaRepository.findBySourceAndPath(sourceId, "show/show.S01E10.mkv").orElseThrow();
        MediaItem episode2 = mediaRepository.findBySourceAndPath(sourceId, "show/show.S01E02.mkv").orElseThrow();
        mediaRepository.saveStructure(episode10.requireId(),
                new MediaStructure(VideoKind.TV_EPISODE, "local:series:show", "Show", 1, 10, null));
        mediaRepository.saveStructure(episode2.requireId(),
                new MediaStructure(VideoKind.TV_EPISODE, "local:series:show", "Show", 1, 2, null));

        assertThat(mediaRepository.find(MediaQuery.of(MediaCategory.VIDEO)))
                .extracting(MediaItem::fileName)
                .containsExactly("show.S01E02.mkv", "show.S01E10.mkv");
    }

    @Test
    void ajBezTmdbTokenuSkenRozpoznaSerialAZoradiEpizody() {
        SmbSource source = sourceRepository.save(newSource());
        long sourceId = source.requireId();
        MediaItem scanned = item(sourceId, "serialy/Dark/Season 1/Dark.S01E02.mkv", "Dark.S01E02.mkv");
        mediaRepository.upsert(scanned, 1L);

        metadataEnrichmentService.enrich(scanned, metadataEnrichmentService.newSession());

        MediaItem loaded = mediaRepository.findBySourceAndPath(
                sourceId, "serialy/Dark/Season 1/Dark.S01E02.mkv").orElseThrow();
        assertThat(loaded.metadata())
                .isNotNull()
                .satisfies(metadata -> {
                    assertThat(metadata.kind()).isEqualTo(VideoKind.TV_EPISODE);
                    assertThat(metadata.groupKey()).isEqualTo("local:series:dark");
                    assertThat(metadata.groupTitle()).isEqualTo("Dark");
                    assertThat(metadata.seasonNumber()).isEqualTo(1);
                    assertThat(metadata.episodeNumber()).isEqualTo(2);
                    assertThat(metadata.status()).isEqualTo(MetadataStatus.PENDING);
                });
    }

    private static SmbSource newSource() {
        return new SmbSource(null, "NAS", "192.168.1.10", 445, "media", "",
                null, "juraj", "tajne", true, null, null);
    }

    private static MediaItem item(long sourceId, String path, String fileName) {
        return new MediaItem(null, sourceId, MediaCategory.VIDEO, path, fileName,
                MediaClassifier.titleOf(fileName), MediaClassifier.extensionOf(fileName),
                1024, Instant.parse("2026-01-01T10:00:00Z"), "video/x-matroska", null, null, null);
    }
}
