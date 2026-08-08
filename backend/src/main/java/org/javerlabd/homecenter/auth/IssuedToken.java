package org.javerlabd.homecenter.auth;

import java.time.Instant;

/**
 * Čerstvo vydaný token aj s otvorenou hodnotou. Toto je jediný okamih, keď server
 * token pozná — do databázy ide už len jeho hash, takže sa nedá znovu zobraziť.
 */
public record IssuedToken(String token, Instant expiresAt) {
}
