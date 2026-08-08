package org.javerland.homecenter.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.javerland.homecenter.config.HomeCenterProperties;
import org.javerland.homecenter.user.AppUser;
import org.javerland.homecenter.user.UserCredentialsChangedEvent;
import org.javerland.homecenter.user.UserService;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies authentication tokens for the Android TV client.
 *
 * <p>The token consists of 256 bits from {@link SecureRandom}. Only its SHA-256 hash is
 * stored in the database, so access to the index file does not grant access to the media
 * center. Argon2 is intentionally not used here: the token is verified on every request,
 * including streaming, and a slow hash adds no protection against guessing a random
 * 256-bit value.
 */
@Service
@Slf4j
public class AuthTokenService {

    private static final int TOKEN_BYTES = 32;

    /**
     * How often {@code last_used_at} is actually written. Without this throttle, every
     * streamed video block would cause a database UPDATE.
     */
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);

    private final SecureRandom random = new SecureRandom();
    private final AuthTokenRepository repository;
    private final UserService userService;
    private final Duration validity;

    public AuthTokenService(AuthTokenRepository repository,
                            UserService userService,
                            HomeCenterProperties properties) {
        this.repository = repository;
        this.userService = userService;
        this.validity = properties.security().tokenValidity();
    }

    public IssuedToken issue(AppUser user, @Nullable String deviceName) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(validity);
        repository.insert(new AuthToken(
                null, user.requireId(), hash(token), trimToNull(deviceName), now, expiresAt, null));
        log.info("Vydaný token pre {} ({}), platí do {}", user.username(),
                deviceName == null ? "neznáme zariadenie" : deviceName, expiresAt);
        return new IssuedToken(token, expiresAt);
    }

    /**
     * Resolves a token to a user. Returns empty for an unknown or expired token and for
     * a token belonging to an account that has since been disabled.
     */
    public Optional<AppUser> resolve(String rawToken) {
        Optional<AuthToken> found = repository.findByHash(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AuthToken token = found.get();
        Instant now = Instant.now();
        if (token.isExpired(now)) {
            repository.deleteById(token.id());
            return Optional.empty();
        }
        Optional<AppUser> user = userService.findById(token.userId()).filter(AppUser::enabled);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        if (token.lastUsedAt() == null || token.lastUsedAt().isBefore(now.minus(TOUCH_INTERVAL))) {
            repository.touch(token.id(), now);
        }
        return user;
    }

    public List<AuthToken> devicesOf(long userId) {
        return repository.findByUser(userId);
    }

    /** Logs out one device. */
    public void revoke(String rawToken) {
        repository.deleteByHash(hash(rawToken));
    }

    /**
     * Logs out all of a user's devices. Called when the password changes or the account
     * is disabled or deleted; otherwise, an old token would survive the change intended
     * to invalidate it.
     */
    public int revokeAllFor(long userId) {
        int revoked = repository.deleteByUser(userId);
        if (revoked > 0) {
            log.info("Zneplatnených {} tokenov používateľa #{}", revoked, userId);
        }
        return revoked;
    }

    /**
     * A password or PIN change, or disabling the account, logs out all of the user's TVs.
     * Account deletion is not handled here because {@code ON DELETE CASCADE} removes the
     * tokens at the schema level.
     */
    @EventListener
    public void onCredentialsChanged(UserCredentialsChangedEvent event) {
        int revoked = revokeAllFor(event.userId());
        if (revoked > 0) {
            log.info("Dôvod odhlásenia zariadení používateľa #{}: {}", event.userId(), event.reason());
        }
    }

    /** Expired tokens are removed at night, shortly before the scheduled scan. */
    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpired() {
        int purged = repository.deleteExpired(Instant.now());
        if (purged > 0) {
            log.info("Odstránených {} expirovaných tokenov", purged);
        }
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 musí byť dostupné", ex);
        }
    }

    private static @Nullable String trimToNull(@Nullable String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
