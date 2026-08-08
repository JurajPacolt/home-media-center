package org.javerlabd.homecenter.source;

import java.io.Closeable;

import com.hierynomus.smbj.share.File;

/**
 * Otvorený súbor na Sambe s možnosťou čítať z ľubovoľnej pozície. Práve toto drží
 * pretáčanie vo videu — Range request sa preloží na čítanie od danej pozície,
 * nie na preskakovanie bajtov od začiatku súboru.
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
     * @return počet načítaných bajtov, alebo -1 na konci súboru
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
