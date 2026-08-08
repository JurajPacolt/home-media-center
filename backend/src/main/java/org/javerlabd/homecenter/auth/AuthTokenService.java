package org.javerlabd.homecenter.auth;

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
import org.javerlabd.homecenter.config.HomeCenterProperties;
import org.javerlabd.homecenter.user.AppUser;
import org.javerlabd.homecenter.user.UserCredentialsChangedEvent;
import org.javerlabd.homecenter.user.UserService;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Vydávanie a overovanie prihlasovacích tokenov pre Android TV klienta.
 *
 * <p>Token je 256 bitov z {@link SecureRandom}. V databáze je len jeho SHA-256 —
 * kto sa dostane k súboru s indexom, nedostane sa tým do mediacentra. Argon2 sa sem
 * zámerne nepoužíva: overuje sa pri každom requeste vrátane streamovania a proti
 * hádaniu 256-bitového náhodného čísla pomalý hash aj tak nič nerieši.
 */
@Service
@Slf4j
public class AuthTokenService {

    private static final int TOKEN_BYTES = 32;

    /**
     * Ako často sa naozaj zapíše {@code last_used_at}. Bez tejto brzdy by každý blok
     * streamovaného videa znamenal UPDATE do databázy.
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
     * Preloží token na používateľa. Vracia prázdno pri neznámom, expirovanom aj pri
     * tokene účtu, ktorý medzitým niekto vypol.
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

    /** Odhlásenie jedného zariadenia. */
    public void revoke(String rawToken) {
        repository.deleteByHash(hash(rawToken));
    }

    /**
     * Odhlási všetky zariadenia používateľa. Volá sa pri zmene hesla, vypnutí účtu
     * aj zmazaní — inak by starý token prežil zmenu, ktorá ho mala zneplatniť.
     */
    public int revokeAllFor(long userId) {
        int revoked = repository.deleteByUser(userId);
        if (revoked > 0) {
            log.info("Zneplatnených {} tokenov používateľa #{}", revoked, userId);
        }
        return revoked;
    }

    /**
     * Zmena hesla, PINu alebo vypnutie účtu odhlási všetky televízory daného používateľa.
     * Zmazanie účtu tu nie je — tokeny zmetie {@code ON DELETE CASCADE} v schéme.
     */
    @EventListener
    public void onCredentialsChanged(UserCredentialsChangedEvent event) {
        int revoked = revokeAllFor(event.userId());
        if (revoked > 0) {
            log.info("Dôvod odhlásenia zariadení používateľa #{}: {}", event.userId(), event.reason());
        }
    }

    /** Expirované tokeny sa upratujú v noci, tesne pred naplánovaným skenom. */
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
