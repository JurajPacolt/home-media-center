package org.javerlabd.homecenter.user;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Media center user. Passwords and PINs are never stored in plaintext; the fields contain
 * Argon2 hashes, and {@link #toString()} intentionally omits them to keep them out of logs.
 */
public record AppUser(
        @Nullable Long id,
        String username,
        String displayName,
        String passwordHash,
        @Nullable String pinHash,
        Role role,
        boolean enabled,
        boolean mustChangePassword,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt) {

    public long requireId() {
        if (id == null) {
            throw new IllegalStateException("Používateľ ešte nebol uložený");
        }
        return id;
    }

    /** Without a PIN, login requires a password and therefore a TV keyboard. */
    public boolean hasPin() {
        return pinHash != null && !pinHash.isBlank();
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public String toString() {
        return "AppUser[id=%s, username=%s, role=%s, enabled=%s, pin=%s]"
                .formatted(id, username, role, enabled, hasPin() ? "nastavený" : "žiadny");
    }
}
