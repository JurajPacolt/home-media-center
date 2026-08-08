package org.javerlabd.homecenter.stream;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Poskladanie HTTP odpovede zo streamu. Používa to REST API pre TV klienta aj náhľad
 * v management UI — obe cesty musia posielať rovnaké hlavičky, inak by sa pretáčanie
 * chovalo v prehliadači inak než na televízore.
 */
public final class MediaStreamResponse {

    private MediaStreamResponse() {
    }

    /**
     * Prehrávanie na mieste — prehliadač aj televízor si obsah vykreslia sami.
     *
     * @param headOnly pri HEAD sa telo neposiela — súbor zo Samby netreba čítať,
     *                 len uvoľniť handle
     */
    public static ResponseEntity<Resource> of(MediaStream stream, boolean headOnly) {
        return build(stream, headOnly, ContentDisposition.inline());
    }

    /**
     * To isté, ale s {@code attachment} — prehliadač súbor uloží namiesto toho, aby ho
     * skúšal prehrať. Pre formáty, ktoré natívne nezvláda (mkv, avi, heic), je stiahnutie
     * jediné, čo mu v management UI ostáva.
     */
    public static ResponseEntity<Resource> ofDownload(MediaStream stream, boolean headOnly) {
        return build(stream, headOnly, ContentDisposition.attachment());
    }

    /**
     * Prehliadač si musí smieť odpoveď odložiť do cache. Chrome stavia prehrávanie na
     * multibufferi nad HTTP cache — s {@code no-store}, ktoré Spring Security pridáva
     * do všetkých odpovedí, nedostane ani prvý blok a načítanie skončí na {@code stalled}.
     * Obsah je súkromný, preto {@code private}.
     */
    private static final String CACHE_CONTROL = "private, max-age=60";

    private static ResponseEntity<Resource> build(MediaStream stream,
                                                  boolean headOnly,
                                                  ContentDisposition.Builder disposition) {
        ResponseEntity.BodyBuilder response = ResponseEntity
                .status(stream.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                .contentType(contentTypeOf(stream))
                .contentLength(stream.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        disposition.filename(stream.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString());

        if (stream.partial()) {
            response.header(HttpHeaders.CONTENT_RANGE, stream.contentRange());
        }
        if (headOnly) {
            stream.close();
            return response.build();
        }
        return response.body(stream.resource());
    }

    private static MediaType contentTypeOf(MediaStream stream) {
        try {
            return MediaType.parseMediaType(stream.contentType());
        } catch (InvalidMediaTypeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
