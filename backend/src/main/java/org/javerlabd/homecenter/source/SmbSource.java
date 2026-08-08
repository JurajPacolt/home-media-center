package org.javerlabd.homecenter.source;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Samba source configuration. Only the server knows the password; {@link #toString()}
 * intentionally omits it so it never appears in logs.
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

    /** Connection identity; changing it requires discarding the connection cache. */
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
