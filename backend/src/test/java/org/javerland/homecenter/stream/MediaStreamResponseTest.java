package org.javerland.homecenter.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Response headers. The same code serves the TV REST API and management UI preview;
 * only Content-Disposition differs.
 */
class MediaStreamResponseTest {

    private static final byte[] CONTENT = "0123456789".getBytes(StandardCharsets.US_ASCII);

    @Test
    void nahladPosielaInlineNechSiToPrehliadacVykresliSam() {
        var response = MediaStreamResponse.of(stream(null), false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("inline")
                .contains("matrix.mkv");
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
    }

    @Test
    void stiahnutiePosielaAttachmentNechToPrehliadacUlozi() {
        var response = MediaStreamResponse.ofDownload(stream(null), false);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment")
                .contains("matrix.mkv");
    }

    /**
     * Media must be cacheable by the browser. Spring Security adds {@code no-store} to
     * responses, removing the multiple buffers on which seeking relies. Therefore,
     * {@code SecurityConfig} omits it for these addresses, and this header is used instead.
     */
    @Test
    void mediaNesuNoStoreAleSukromneCacheovatelne() {
        var response = MediaStreamResponse.of(stream(null), false);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("private, max-age=60")
                .doesNotContain("no-store");
    }

    @Test
    void stiahnutieRovnakoPodporujeRangeAko206() {
        var response = MediaStreamResponse.ofDownload(stream("bytes 2-5/10"), false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 2-5/10");
    }

    @Test
    void nezmyselnyContentTypeSpadneNaOctetStreamMiestoVynimky() {
        MediaStream stream = new MediaStream(
                new KnownLengthResource(new ByteArrayInputStream(CONTENT), CONTENT.length, "x.bin"),
                "toto nie je mime typ", CONTENT.length, CONTENT.length, null, "x.bin");

        var response = MediaStreamResponse.of(stream, false);

        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }

    @Test
    void headNeposielaTeloAleZatvoriHandle() {
        MediaStream stream = stream(null);

        var response = MediaStreamResponse.of(stream, true);

        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders().getContentLength()).isEqualTo(CONTENT.length);
    }

    private static MediaStream stream(String contentRange) {
        int length = contentRange == null ? CONTENT.length : 4;
        return new MediaStream(
                new KnownLengthResource(new ByteArrayInputStream(CONTENT), length, "matrix.mkv"),
                "video/x-matroska", length, CONTENT.length, contentRange, "matrix.mkv");
    }
}
