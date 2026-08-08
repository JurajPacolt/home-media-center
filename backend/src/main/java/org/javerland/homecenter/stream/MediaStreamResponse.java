package org.javerland.homecenter.stream;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Assembles an HTTP response from a stream. Both the TV client's REST API and the
 * management UI preview use this; both paths must send identical headers, or seeking
 * would behave differently in the browser and on the TV.
 */
public final class MediaStreamResponse {

    private MediaStreamResponse() {
    }

    /**
     * Inline playback; the browser and TV render the content themselves.
     *
     * @param headOnly for HEAD, the body is not sent; the Samba file need not be read,
     *                 only its handle released
     */
    public static ResponseEntity<Resource> of(MediaStream stream, boolean headOnly) {
        return build(stream, headOnly, ContentDisposition.inline());
    }

    /**
     * The same response with {@code attachment}; the browser saves the file instead of
     * attempting playback. For formats it does not support natively (mkv, avi, heic),
     * downloading is the management UI's only remaining option.
     */
    public static ResponseEntity<Resource> ofDownload(MediaStream stream, boolean headOnly) {
        return build(stream, headOnly, ContentDisposition.attachment());
    }

    /**
     * The browser must be allowed to cache the response. Chrome builds playback on multiple
     * buffers over the HTTP cache; with the {@code no-store} that Spring Security adds to
     * every response, it does not receive even the first block and loading ends at
     * {@code stalled}. The content is private, hence {@code private}.
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
