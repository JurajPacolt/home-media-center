package org.javerlabd.homecenter.stream;

import java.io.InputStream;
import java.util.Objects;

import org.javerlabd.homecenter.source.SmbReadHandle;

/**
 * Číta presne vymedzené okno súboru na Sambe. Čítanie ide priamo na pozíciu, takže
 * pretáčanie na koniec filmu nestojí prenos celého súboru.
 */
final class SmbInputStream extends InputStream {

    private final SmbReadHandle handle;
    private final long endExclusive;
    private long position;

    SmbInputStream(SmbReadHandle handle, long start, long length) {
        this.handle = handle;
        this.position = start;
        this.endExclusive = start + length;
    }

    @Override
    public int read() {
        byte[] single = new byte[1];
        int read = read(single, 0, 1);
        return read < 0 ? -1 : single[0] & 0xFF;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, buffer.length);
        if (length == 0) {
            return 0;
        }
        long remaining = endExclusive - position;
        if (remaining <= 0) {
            return -1;
        }
        int read = handle.read(buffer, position, offset, (int) Math.min(length, remaining));
        if (read <= 0) {
            return -1;
        }
        position += read;
        return read;
    }

    /** Preskočenie je len posun pozície — žiadne bajty sa cez sieť neťahajú. */
    @Override
    public long skip(long count) {
        if (count <= 0) {
            return 0;
        }
        long skipped = Math.min(count, endExclusive - position);
        position += skipped;
        return skipped;
    }

    @Override
    public int available() {
        return (int) Math.clamp(endExclusive - position, 0, Integer.MAX_VALUE);
    }

    @Override
    public void close() {
        handle.close();
    }
}
