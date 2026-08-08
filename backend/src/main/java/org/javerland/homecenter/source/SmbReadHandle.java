package org.javerland.homecenter.source;

import java.io.Closeable;

import com.hierynomus.smbj.share.File;

/**
 * Open Samba file that supports reading from any position. This enables video seeking:
 * a Range request becomes a read from the requested position rather than skipping bytes
 * from the beginning of the file.
 */
public final class SmbReadHandle implements Closeable {

    private final File file;
    private final long size;
    private final String path;

    SmbReadHandle(File file, long size, String path) {
        this.file = file;
        this.size = size;
        this.path = path;
    }

    /**
     * @return number of bytes read, or -1 at end of file
     */
    public int read(byte[] buffer, long fileOffset, int bufferOffset, int length) {
        try {
            return file.read(buffer, fileOffset, bufferOffset, length);
        } catch (RuntimeException ex) {
            throw new SmbAccessException("Čítanie súboru " + path + " zlyhalo", ex);
        }
    }

    public long size() {
        return size;
    }

    public String path() {
        return path;
    }

    @Override
    public void close() {
        file.close();
    }
}
