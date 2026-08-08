package org.javerlabd.homecenter.auth;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Prihlásené zariadenie. V databáze je len SHA-256 tokenu — samotný token vidí server
 * jediný raz, keď ho vydáva, a potom už len klient.
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
