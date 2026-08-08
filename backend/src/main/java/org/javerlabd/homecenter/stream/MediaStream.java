package org.javerlabd.homecenter.stream;

import java.io.Closeable;

import org.jspecify.annotations.Nullable;

/**
 * Pripravená odpoveď na stream požiadavku. Controller z nej už len poskladá hlavičky —
 * rozhodnutie o rozsahu padlo tu, v service vrstve.
 */
public record MediaStream(
        KnownLengthResource resource,
        String contentType,
        long contentLength,
        long totalLength,
        @Nullable String contentRange,
        String fileName) implements Closeable {

    /** true, ak sa posiela iba časť súboru (odpoveď 206). */
    public boolean partial() {
        return contentRange != null;
    }

    @Override
    public void close() {
        resource.close();
    }
}
