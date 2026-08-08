package org.javerlabd.homecenter.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import org.javerlabd.homecenter.config.HomeCenterProperties;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class MediaClassifierTest {

    private final MediaClassifier classifier = new MediaClassifier(new HomeCenterProperties(
            Path.of("target"),
            new HomeCenterProperties.Library(
                    Set.of("mkv", "mp4"),
                    Set.of("jpg", "png"),
                    Set.of("mp3", "flac")),
            new HomeCenterProperties.Metadata("", "sk-SK", "en-US",
                    Duration.ofDays(30), Duration.ofDays(1), Duration.ZERO,
                    "https://api.themoviedb.org/3", "https://image.tmdb.org/t/p/w342"),
            new HomeCenterProperties.Scan("-", false, 32),
            new HomeCenterProperties.Streaming(DataSize.ofKilobytes(256)),
            new HomeCenterProperties.Security(Duration.ofDays(90))));

    @Test
    void priponaUrciKategoriu() {
        assertThat(classifier.categoryOf("Matrix.mkv")).contains(MediaCategory.VIDEO);
        assertThat(classifier.categoryOf("dovolenka.JPG")).contains(MediaCategory.PHOTO);
        assertThat(classifier.categoryOf("pesnicka.flac")).contains(MediaCategory.AUDIO);
    }

    @Test
    void neznamaPriponaSaDoKniznceNedostane() {
        assertThat(classifier.categoryOf("titulky.srt")).isEmpty();
        assertThat(classifier.categoryOf("readme")).isEmpty();
    }

    @Test
    void contentTypeSedíNaPriponu() {
        assertThat(classifier.contentTypeOf("Matrix.mkv")).isEqualTo("video/x-matroska");
        assertThat(classifier.contentTypeOf("pesnicka.flac")).isEqualTo("audio/flac");
        assertThat(classifier.contentTypeOf("cosi.xyz")).isEqualTo("application/octet-stream");
    }

    @Test
    void nazovSaUpraceOdOddelovacov() {
        assertThat(MediaClassifier.titleOf("The.Matrix.1999.1080p.mkv")).isEqualTo("The Matrix 1999 1080p");
        assertThat(MediaClassifier.titleOf("moja_pesnicka.mp3")).isEqualTo("moja pesnicka");
        assertThat(MediaClassifier.titleOf("bezpripony")).isEqualTo("bezpripony");
    }

    @Test
    void extensionOfZvladneAjSuborBezPripony() {
        assertThat(MediaClassifier.extensionOf("Matrix.MKV")).isEqualTo("mkv");
        assertThat(MediaClassifier.extensionOf("archiv.tar.gz")).isEqualTo("gz");
        assertThat(MediaClassifier.extensionOf("bezpripony")).isEmpty();
    }
}
