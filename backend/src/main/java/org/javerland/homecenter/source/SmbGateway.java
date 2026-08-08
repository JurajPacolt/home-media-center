package org.javerland.homecenter.source;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.Share;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * The only place that accesses Samba. The share connection remains open and is shared
 * between requests because establishing an SMB session is expensive; otherwise, scanning
 * and streaming would pay that cost for every file.
 */
@Component
@Slf4j
public class SmbGateway implements DisposableBean {

    private final SMBClient client = new SMBClient();
    private final Map<Long, LiveShare> shares = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Verifies that the source is reachable and its root directory exists. It uses a
     * dedicated connection that is closed immediately, so a management UI test does not
     * interrupt an active stream and also works for a source not yet stored.
     */
    public void verify(SmbSource source) {
        String root = SmbPaths.toSmb(source.rootPath());
        LiveShare live = connect(source, source.connectionFingerprint());
        try {
            if (!root.isEmpty() && !live.share().folderExists(root)) {
                throw new SmbAccessException("Priečinok '" + source.rootPath()
                        + "' na share-i " + source.shareName() + " neexistuje");
            }
        } catch (SmbAccessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new SmbAccessException("Overenie zdroja zlyhalo: " + ex.getMessage(), ex);
        } finally {
            closeQuietly(live);
        }
    }

    /**
     * @param relativePath path relative to the source root ('/' separator, empty for root)
     */
    public List<SmbEntry> list(SmbSource source, String relativePath) {
        String smbPath = SmbPaths.toSmb(SmbPaths.join(source.rootPath(), relativePath));
        return withShare(source, share -> {
            List<SmbEntry> entries = new ArrayList<>();
            for (FileIdBothDirectoryInformation info : share.list(smbPath)) {
                String name = info.getFileName();
                if (".".equals(name) || "..".equals(name)) {
                    continue;
                }
                long attributes = info.getFileAttributes();
                // Reparse points can create scan loops, and system files do not belong in the library.
                if (isSet(attributes, FileAttributes.FILE_ATTRIBUTE_SYSTEM)
                        || isSet(attributes, FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT)) {
                    continue;
                }
                entries.add(new SmbEntry(
                        SmbPaths.join(relativePath, name),
                        name,
                        isSet(attributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY),
                        info.getEndOfFile(),
                        info.getLastWriteTime() == null ? null : info.getLastWriteTime().toInstant()));
            }
            return entries;
        });
    }

    public SmbReadHandle openForRead(SmbSource source, String relativePath) {
        String smbPath = SmbPaths.toSmb(SmbPaths.join(source.rootPath(), relativePath));
        return withShare(source, share -> {
            long size = share.getFileInformation(smbPath).getStandardInformation().getEndOfFile();
            var file = share.openFile(
                    smbPath,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                    SMB2CreateDisposition.FILE_OPEN,
                    null);
            return new SmbReadHandle(file, size, relativePath);
        });
    }

    /** Discards the source connection after its settings change. */
    public void invalidate(long sourceId) {
        lock.lock();
        try {
            closeQuietly(shares.remove(sourceId));
        } finally {
            lock.unlock();
        }
    }

    private static boolean isSet(long attributes, FileAttributes attribute) {
        return (attributes & attribute.getValue()) != 0;
    }

    /**
     * The first attempt uses the cached connection. Failure usually means the server has
     * dropped the session, so the second attempt uses a fresh connection.
     */
    private <T> T withShare(SmbSource source, ShareOperation<T> operation) {
        try {
            return operation.apply(share(source, false));
        } catch (SmbAccessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("SMB operácia na {} zlyhala ({}), skúšam nové pripojenie",
                    source.host(), ex.toString());
            try {
                return operation.apply(share(source, true));
            } catch (SmbAccessException retryFailure) {
                throw retryFailure;
            } catch (RuntimeException retryFailure) {
                throw new SmbAccessException("Prístup na " + source.host() + "/"
                        + source.shareName() + " zlyhal: " + retryFailure.getMessage(), retryFailure);
            }
        }
    }

    private DiskShare share(SmbSource source, boolean forceReconnect) {
        long id = source.requireId();
        String fingerprint = source.connectionFingerprint();
        lock.lock();
        try {
            LiveShare live = shares.get(id);
            boolean usable = live != null
                    && live.fingerprint().equals(fingerprint)
                    && live.connection().isConnected();
            if (usable && !forceReconnect) {
                return live.share();
            }
            closeQuietly(live);
            shares.remove(id);

            LiveShare connected = connect(source, fingerprint);
            shares.put(id, connected);
            return connected.share();
        } finally {
            lock.unlock();
        }
    }

    private LiveShare connect(SmbSource source, String fingerprint) {
        Connection connection = null;
        try {
            connection = client.connect(source.host(), source.port());
            Session session = connection.authenticate(authentication(source));
            Share share = session.connectShare(source.shareName());
            if (!(share instanceof DiskShare diskShare)) {
                throw new SmbAccessException("'" + source.shareName() + "' nie je diskový share");
            }
            log.info("Pripojený SMB zdroj {}:{}/{}", source.host(), source.port(), source.shareName());
            return new LiveShare(fingerprint, connection, session, diskShare);
        } catch (IOException | RuntimeException ex) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException | RuntimeException ignored) {
                    // The original error is more informative.
                }
            }
            if (ex instanceof SmbAccessException accessException) {
                throw accessException;
            }
            throw new SmbAccessException("Nepodarilo sa pripojiť na " + source.host() + ":"
                    + source.port() + "/" + source.shareName() + " — " + ex.getMessage(), ex);
        }
    }

    private static AuthenticationContext authentication(SmbSource source) {
        String username = source.username();
        if (username == null || username.isBlank()) {
            return AuthenticationContext.anonymous();
        }
        String password = source.password() == null ? "" : source.password();
        String domain = (source.domain() == null || source.domain().isBlank()) ? null : source.domain();
        return new AuthenticationContext(username, password.toCharArray(), domain);
    }

    private static void closeQuietly(LiveShare live) {
        if (live == null) {
            return;
        }
        try {
            live.connection().close();
        } catch (IOException | RuntimeException ex) {
            log.debug("Zatvorenie SMB spojenia zlyhalo: {}", ex.toString());
        }
    }

    @Override
    public void destroy() {
        lock.lock();
        try {
            shares.values().forEach(SmbGateway::closeQuietly);
            shares.clear();
        } finally {
            lock.unlock();
        }
        client.close();
    }

    private record LiveShare(String fingerprint, Connection connection, Session session, DiskShare share) {
    }

    @FunctionalInterface
    private interface ShareOperation<T> {
        T apply(DiskShare share);
    }
}
