package org.javerlabd.homecenter.stream;

import java.io.Closeable;

import org.jspecify.annotations.Nullable;

/**
 * Prepared response to a streaming request. The controller only assembles headers from it;
 * the range decision was made here in the service layer.
 */
public record MediaStream(
        KnownLengthResource resource,
        String contentType,
        long contentLength,
        long totalLength,
        @Nullable String contentRange,
        String fileName) implements Closeable {

    /** True when only part of the file is sent (a 206 response). */
    public boolean partial() {
        return contentRange != null;
    }

    @Override
    public void close() {
        resource.close();
    }
}
