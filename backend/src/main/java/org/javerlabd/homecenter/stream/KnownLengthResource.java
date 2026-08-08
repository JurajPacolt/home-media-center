package org.javerlabd.homecenter.stream;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.AbstractResource;

/**
 * Resource over an already open stream whose length is known in advance.
 * {@code InputStreamResource} would determine it by reading all content, which would
 * download a Samba movie twice.
 */
@Slf4j
public final class KnownLengthResource extends AbstractResource implements Closeable {

    private final InputStream stream;
    private final long length;
    private final String fileName;
    private boolean consumed;

    public KnownLengthResource(InputStream stream, long length, String fileName) {
        this.stream = stream;
        this.length = length;
        this.fileName = fileName;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public long contentLength() {
        return length;
    }

    @Override
    public String getFilename() {
        return fileName;
    }

    @Override
    public String getDescription() {
        return "stream [" + fileName + "]";
    }

    @Override
    public synchronized InputStream getInputStream() {
        if (consumed) {
            throw new IllegalStateException("Stream " + fileName + " sa dá prečítať iba raz");
        }
        consumed = true;
        return stream;
    }

    /** Releases the resource if the response body was not sent, for example on HEAD. */
    @Override
    public synchronized void close() {
        if (consumed) {
            return;
        }
        consumed = true;
        try {
            stream.close();
        } catch (IOException ex) {
            log.debug("Zatvorenie streamu {} zlyhalo: {}", fileName, ex.toString());
        }
    }
}
