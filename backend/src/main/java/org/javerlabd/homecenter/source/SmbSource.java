package org.javerlabd.homecenter.source;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Nastavenie Samba zdroja. Heslo pozná iba server — {@link #toString()} ho zámerne
 * nevypisuje, aby sa neobjavilo v logu.
 */
public record SmbSource(
        @Nullable Long id,
        String name,
        String host,
        int port,
        String shareName,
        String rootPath,
        @Nullable String domain,
        @Nullable String username,
        @Nullable String password,
        boolean enabled,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt) {

    public static final int DEFAULT_PORT = 445;

    public long requireId() {
        if (id == null) {
            throw new IllegalStateException("Zdroj ešte nebol uložený");
        }
        return id;
    }

    /** Zmení sa ním identita pripojenia — pri zmene treba zahodiť cache spojení. */
    public String connectionFingerprint() {
        return host + ":" + port + "/" + shareName + "@" + (domain == null ? "" : domain)
                + "/" + (username == null ? "" : username) + "#" + (password == null ? 0 : password.hashCode());
    }

    public SmbSource withPassword(@Nullable String newPassword) {
        return new SmbSource(id, name, host, port, shareName, rootPath, domain, username,
                newPassword, enabled, createdAt, updatedAt);
    }

    @Override
    public String toString() {
        return "SmbSource[id=%s, name=%s, %s:%d/%s, rootPath=%s, username=%s, enabled=%s]"
                .formatted(id, name, host, port, shareName, rootPath, username, enabled);
    }
}
