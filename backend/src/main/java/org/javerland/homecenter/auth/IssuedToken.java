package org.javerland.homecenter.auth;

import java.time.Instant;

/**
 * A newly issued token including its plaintext value. This is the only time the server
 * knows the token; only its hash is stored in the database, so it cannot be shown again.
 */
public record IssuedToken(String token, Instant expiresAt) {
}
