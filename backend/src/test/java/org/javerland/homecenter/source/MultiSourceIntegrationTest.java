package org.javerland.homecenter.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.javerland.homecenter.media.MediaCategory;
import org.javerland.homecenter.media.MediaClassifier;
import org.javerland.homecenter.media.MediaItem;
import org.javerland.homecenter.media.MediaQuery;
import org.javerland.homecenter.media.MediaRepository;
import org.javerland.homecenter.media.MediaService;
import org.javerland.homecenter.media.SourceUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Multiple Samba sources sharing one index. Items must remain associated with their source;
 * otherwise, streaming would access the wrong NAS.
 */
@SpringBootTest
@ActiveProfiles("test")
class MultiSourceIntegrationTest {

    @Autowired
    private SmbSourceService sourceService;

    @Autowired
    private MediaService mediaService;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clear() {
        jdbc.sql("DELETE FROM media_item").update();
        jdbc.sql("DELETE FROM scan_run").update();
        jdbc.sql("DELETE FROM smb_source").update();
    }

    @Test
    void daSaUlozitViacZdrojovNaraz() {
        sourceService.save(source("Filmy NAS", "192.168.1.10", "media"));
        sourceService.save(source("Archív", "192.168.1.20", "archiv"));

        assertThat(sourceService.findAll())
                .extracting(SmbSource::name)
                .containsExactlyInAnyOrder("Filmy NAS", "Archív");
    }

    @Test
    void rovnakyNazovSaOdmietneBezOhladuNaVelkostPismen() {
        sourceService.save(source("Filmy NAS", "192.168.1.10", "media"));

        assertThatThrownBy(() -> sourceService.save(source("filmy nas", "192.168.1.20", "ine")))
                .isInstanceOf(DuplicateSourceNameException.class);
    }

    @Test
    void upravaZdrojaNenarazaNaJehoVlastnyNazov() {
        SmbSource saved = sourceService.save(source("Filmy NAS", "192.168.1.10", "media"));

        SmbSource renamed = sourceService.save(new SmbSource(saved.id(), "Filmy NAS", "192.168.1.11",
                445, "media", "", null, null, null, true, null, null));

        assertThat(renamed.host()).isEqualTo("192.168.1.11");
    }

    @Test
    void rovnakaCestaNaDvochZdrojochSuDveRoznePolozky() {
        long prvy = sourceService.save(source("Filmy NAS", "192.168.1.10", "media")).requireId();
        long druhy = sourceService.save(source("Archív", "192.168.1.20", "archiv")).requireId();

        assertThat(mediaRepository.upsert(item(prvy, "filmy/matrix.mkv"), 1L)).isTrue();
        assertThat(mediaRepository.upsert(item(druhy, "filmy/matrix.mkv"), 2L))
                .as("unikátny index je nad (source_id, relative_path), nie nad samotnou cestou")
                .isTrue();

        assertThat(mediaRepository.count(new MediaQuery(null, null, null, null, 50, 0))).isEqualTo(2);
    }

    @Test
    void vypisSaDaZuzitNaJedenZdroj() {
        long prvy = sourceService.save(source("Filmy NAS", "192.168.1.10", "media")).requireId();
        long druhy = sourceService.save(source("Archív", "192.168.1.20", "archiv")).requireId();
        mediaRepository.upsert(item(prvy, "matrix.mkv"), 1L);
        mediaRepository.upsert(item(prvy, "pulp.mkv"), 1L);
        mediaRepository.upsert(item(druhy, "dovolenka.mkv"), 1L);

        assertThat(mediaRepository.find(MediaQuery.ofSource(prvy)))
                .extracting(MediaItem::fileName)
                .containsExactlyInAnyOrder("matrix.mkv", "pulp.mkv");
        assertThat(mediaRepository.count(MediaQuery.ofSource(druhy))).isEqualTo(1);
    }

    @Test
    void filterZdrojaSaKombinujeSKategoriouAjHladanim() {
        long prvy = sourceService.save(source("Filmy NAS", "192.168.1.10", "media")).requireId();
        long druhy = sourceService.save(source("Archív", "192.168.1.20", "archiv")).requireId();
        mediaRepository.upsert(item(prvy, "matrix.mkv"), 1L);
        mediaRepository.upsert(item(druhy, "matrix.mkv"), 1L);

        assertThat(mediaRepository.count(new MediaQuery(MediaCategory.VIDEO, prvy, null, "matrix", 50, 0)))
                .isEqualTo(1);
        assertThat(mediaRepository.count(new MediaQuery(MediaCategory.PHOTO, prvy, null, "matrix", 50, 0)))
                .isZero();
    }

    @Test
    void prehladUkazujeKolkoZaberaKtoryZdroj() {
        long prvy = sourceService.save(source("Filmy NAS", "192.168.1.10", "media")).requireId();
        long druhy = sourceService.save(source("Archív", "192.168.1.20", "archiv")).requireId();
        mediaRepository.upsert(item(prvy, "matrix.mkv"), 1L);
        mediaRepository.upsert(item(prvy, "pulp.mkv"), 1L);
        mediaRepository.upsert(item(druhy, "dovolenka.mkv"), 1L);

        assertThat(mediaService.usageOf(prvy))
                .extracting(SourceUsage::items, SourceUsage::sizeBytes)
                .containsExactly(2L, 2048L);
        assertThat(mediaService.usageOf(druhy).items()).isEqualTo(1);
    }

    @Test
    void zmazanieZdrojaZoberieAjJehoPolozkyAleNieCudzie() {
        long prvy = sourceService.save(source("Filmy NAS", "192.168.1.10", "media")).requireId();
        long druhy = sourceService.save(source("Archív", "192.168.1.20", "archiv")).requireId();
        mediaRepository.upsert(item(prvy, "matrix.mkv"), 1L);
        mediaRepository.upsert(item(druhy, "dovolenka.mkv"), 1L);

        sourceService.delete(prvy);

        assertThat(mediaRepository.count(new MediaQuery(null, null, null, null, 50, 0)))
                .as("ON DELETE CASCADE vyhodí len položky zmazaného zdroja")
                .isEqualTo(1);
        assertThat(mediaRepository.find(MediaQuery.ofSource(druhy)))
                .extracting(MediaItem::fileName)
                .containsExactly("dovolenka.mkv");
    }

    @Test
    void skenBerieLenZapnuteZdrojeAlePozitDruhySaDaVzdy() {
        sourceService.save(source("Filmy NAS", "192.168.1.10", "media"));
        SmbSource vypnuty = sourceService.save(new SmbSource(null, "Archív", "192.168.1.20", 445,
                "archiv", "", null, null, null, false, null, null));

        assertThat(sourceService.findAllEnabled())
                .extracting(SmbSource::name)
                .containsExactly("Filmy NAS");
        assertThat(sourceService.require(vypnuty.requireId()).enabled())
                .as("vypnutý zdroj ostáva dostupný na ručný sken")
                .isFalse();
    }

    @Test
    void bezZapnutehoZdrojaSaSkenNemaOCoOpriet() {
        sourceService.save(new SmbSource(null, "Archív", "192.168.1.20", 445,
                "archiv", "", null, null, null, false, null, null));

        assertThatThrownBy(() -> sourceService.requireEnabled())
                .isInstanceOf(NoActiveSourceException.class);
    }

    @Test
    void nazvyZdrojovSaDajuVytiahnutJednymDotazom() {
        long prvy = sourceService.save(source("Filmy NAS", "192.168.1.10", "media")).requireId();
        long druhy = sourceService.save(source("Archív", "192.168.1.20", "archiv")).requireId();

        assertThat(sourceService.namesById())
                .containsEntry(prvy, "Filmy NAS")
                .containsEntry(druhy, "Archív");
    }

    private static SmbSource source(String name, String host, String share) {
        return new SmbSource(null, name, host, 445, share, "", null, "juraj", "tajne",
                true, null, null);
    }

    private static MediaItem item(long sourceId, String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return new MediaItem(null, sourceId, MediaCategory.VIDEO, path, fileName,
                MediaClassifier.titleOf(fileName), MediaClassifier.extensionOf(fileName),
                1024, Instant.parse("2026-01-01T10:00:00Z"), "video/x-matroska", null, null, null);
    }
}
