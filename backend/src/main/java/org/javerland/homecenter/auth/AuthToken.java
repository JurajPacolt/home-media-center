package org.javerland.homecenter.auth;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Authenticated device. Only the token's SHA-256 hash is stored in the database; the
 * server sees the token itself once when issuing it, after which only the client has it.
 */
public record AuthToken(
        @Nullable Long id,
        long userId,
        String tokenHash,
        @Nullable String deviceName,
        Instant createdAt,
        Instant expiresAt,
        @Nullable Instant lastUsedAt) {

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
