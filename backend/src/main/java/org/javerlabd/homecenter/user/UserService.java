package org.javerlabd.homecenter.user;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User management and credential verification. This is the only place that sees plaintext
 * passwords and PINs; only Argon2 hashes leave it.
 *
 * <p>Shared layer for the management UI and REST API: {@code UserAdminController} builds
 * HTML on top of it, while {@code AuthApiController} builds JSON.
 */
@Service
@Slf4j
public class UserService {

    /** The PIN is for remote-control entry, so it is short and contains digits only. */
    public static final int PIN_MIN_LENGTH = 4;
    public static final int PIN_MAX_LENGTH = 8;
    public static final int PASSWORD_MIN_LENGTH = 8;

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9](?:[a-z0-9._-]{1,62}[a-z0-9])?");
    private static final Pattern PIN = Pattern.compile("\\d{" + PIN_MIN_LENGTH + "," + PIN_MAX_LENGTH + "}");

    private final UserRepository repository;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;

    /**
     * Hash known not to match anything. Verification runs against it even when the user
     * does not exist; otherwise, response timing could reveal which usernames are stored.
     */
    private final String dummyHash;

    public UserService(UserRepository repository,
                       PasswordEncoder encoder,
                       ApplicationEventPublisher events) {
        this.repository = repository;
        this.encoder = encoder;
        this.events = events;
        this.dummyHash = encoder.encode("$neexistujuci-pouzivatel$");
    }

    public List<AppUser> findAll() {
        return repository.findAll();
    }

    public AppUser require(long id) {
        return repository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    public Optional<AppUser> findById(long id) {
        return repository.findById(id);
    }

    public Optional<AppUser> findByUsername(String username) {
        return repository.findByUsername(normalizeUsername(username));
    }

    public boolean noUsersYet() {
        return repository.isEmpty();
    }

    /**
     * Verifies a username and secret. The secret may be a password or, when allowed by
     * the caller and configured for the user, a PIN.
     *
     * @param pinAllowed PINs are accepted only by the TV client's REST API; the management
     *                   UI always requires the full password
     */
    public Optional<AppUser> authenticate(String username, String secret, boolean pinAllowed) {
        Optional<AppUser> found = repository.findByUsername(normalizeUsername(username));
        if (found.isEmpty()) {
            encoder.matches(secret, dummyHash);
            return Optional.empty();
        }
        AppUser user = found.get();
        if (!user.enabled()) {
            encoder.matches(secret, dummyHash);
            log.info("Prihlásenie zamietnuté — účet {} je vypnutý", user.username());
            return Optional.empty();
        }
        if (encoder.matches(secret, user.passwordHash())) {
            return Optional.of(user);
        }
        if (pinAllowed && user.hasPin() && encoder.matches(secret, user.pinHash())) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** Creates or updates a user, including hashing and validation. */
    @Transactional
    public AppUser save(UserDraft draft) {
        String username = normalizeUsername(draft.username());
        if (!USERNAME.matcher(username).matches()) {
            throw new InvalidCredentialFormatException(
                    "Meno smie mať 2–64 znakov: malé písmená, číslice, bodka, pomlčka a podčiarkovník");
        }
        repository.findByUsername(username)
                .filter(other -> draft.id() == null || other.requireId() != draft.id())
                .ifPresent(other -> {
                    throw new DuplicateUsernameException(username);
                });

        AppUser existing = draft.id() == null ? null : require(draft.id());
        if (existing != null) {
            guardLastAdmin(existing, draft.role(), draft.enabled());
        }

        String passwordHash = resolvePasswordHash(draft, existing);
        String pinHash = resolvePinHash(draft, existing);
        String displayName = draft.displayName() == null || draft.displayName().isBlank()
                ? username
                : draft.displayName().trim();

        AppUser saved = repository.save(new AppUser(
                draft.id(),
                username,
                displayName,
                passwordHash,
                pinHash,
                draft.role(),
                draft.enabled(),
                // An administrator entered this password manually; it is no longer a generated default.
                existing != null && existing.mustChangePassword() && !hasText(draft.password()),
                null,
                null));
        log.info("Uložený používateľ {}", saved);

        if (existing != null) {
            revokeIfCredentialsChanged(existing, saved);
        }
        return saved;
    }

    /**
     * A password or PIN change, or disabling the account, must log out authenticated TVs.
     * A role change is excluded because {@code AuthTokenService} loads the role fresh from
     * the database on every request.
     */
    private void revokeIfCredentialsChanged(AppUser before, AppUser after) {
        String reason = null;
        if (!before.passwordHash().equals(after.passwordHash())) {
            reason = "zmenené heslo";
        } else if (!Objects.equals(before.pinHash(), after.pinHash())) {
            reason = "zmenený PIN";
        } else if (before.enabled() && !after.enabled()) {
            reason = "vypnutý účet";
        }
        if (reason != null) {
            events.publishEvent(new UserCredentialsChangedEvent(after.requireId(), reason));
        }
    }

    /** Changes the current user's password and clears the forced-change flag. */
    @Transactional
    public AppUser changePassword(long id, String newPassword) {
        AppUser user = require(id);
        requireValidPassword(newPassword);
        AppUser saved = repository.save(new AppUser(
                user.id(),
                user.username(),
                user.displayName(),
                encoder.encode(newPassword),
                user.pinHash(),
                user.role(),
                user.enabled(),
                false,
                user.createdAt(),
                user.updatedAt()));
        log.info("Zmenené heslo používateľa {}", saved.username());
        events.publishEvent(new UserCredentialsChangedEvent(saved.requireId(), "zmenené heslo"));
        return saved;
    }

    @Transactional
    public void delete(long id) {
        AppUser user = require(id);
        if (user.isAdmin() && user.enabled() && repository.countOtherEnabledAdmins(id) == 0) {
            throw new LastAdminException();
        }
        repository.deleteById(id);
        log.info("Zmazaný používateľ {}", user.username());
    }

    /** Verifies a password against the stored hash before changing the current password. */
    public boolean passwordMatches(AppUser user, String password) {
        return encoder.matches(password, user.passwordHash());
    }

    private String resolvePasswordHash(UserDraft draft, @Nullable AppUser existing) {
        if (hasText(draft.password())) {
            requireValidPassword(draft.password());
            return encoder.encode(draft.password());
        }
        if (existing == null) {
            throw new InvalidCredentialFormatException("Novému používateľovi treba zadať heslo");
        }
        return existing.passwordHash();
    }

    private @Nullable String resolvePinHash(UserDraft draft, @Nullable AppUser existing) {
        if (draft.clearPin()) {
            return null;
        }
        if (hasText(draft.pin())) {
            String pin = draft.pin().trim();
            if (!PIN.matcher(pin).matches()) {
                throw new InvalidCredentialFormatException(
                        "PIN musí mať " + PIN_MIN_LENGTH + "–" + PIN_MAX_LENGTH + " číslic");
            }
            return encoder.encode(pin);
        }
        return existing == null ? null : existing.pinHash();
    }

    /**
     * Prevents an update from removing the last administrator's role or disabling the
     * account, which would lock the server.
     */
    private void guardLastAdmin(AppUser existing, Role newRole, boolean newEnabled) {
        boolean stopsBeingUsableAdmin = existing.isAdmin() && existing.enabled()
                && (newRole != Role.ADMIN || !newEnabled);
        if (stopsBeingUsableAdmin && repository.countOtherEnabledAdmins(existing.requireId()) == 0) {
            throw new LastAdminException();
        }
    }

    private static void requireValidPassword(@Nullable String password) {
        if (password == null || password.trim().length() < PASSWORD_MIN_LENGTH) {
            throw new InvalidCredentialFormatException(
                    "Heslo musí mať aspoň " + PASSWORD_MIN_LENGTH + " znakov");
        }
    }

    private static String normalizeUsername(@Nullable String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.isBlank();
    }
}
