package org.javerlabd.homecenter.user;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Používateľ mediacentra. Heslo ani PIN sa nikdy nedržia v otvorenom tvare — v poliach
 * sú Argon2 hashe a {@link #toString()} ich zámerne nevypisuje, aby neskončili v logu.
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

    /** Bez PINu sa dá prihlásiť len heslom — teda nie z televízora bez klávesnice. */
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
