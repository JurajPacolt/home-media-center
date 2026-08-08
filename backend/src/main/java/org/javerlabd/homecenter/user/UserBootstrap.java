package org.javerlabd.homecenter.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * First launch: without an account, the management UI would be entirely inaccessible,
 * so the server creates the {@code admin} administrator with the {@code admin} password
 * and marks it for a forced password change. {@code PasswordChangeInterceptor} then
 * blocks access to every other page.
 *
 * <p>This intentionally bypasses {@link UserService#save(UserDraft)}: the default password
 * is shorter than validation permits, which is acceptable precisely because it must be
 * changed immediately.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserBootstrap implements ApplicationRunner {

    static final String DEFAULT_USERNAME = "admin";
    static final String DEFAULT_PASSWORD = "admin";

    private final UserRepository repository;
    private final PasswordEncoder encoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!repository.isEmpty()) {
            return;
        }
        repository.save(new AppUser(
                null,
                DEFAULT_USERNAME,
                "Správca",
                encoder.encode(DEFAULT_PASSWORD),
                null,
                Role.ADMIN,
                true,
                true,
                null,
                null));
        log.warn("""

                ┌───────────────────────────────────────────────────────────────┐
                │  Prvé spustenie — vytvorený správca:                          │
                │      meno:  {}                                            │
                │      heslo: {}                                            │
                │  Server ťa pri prihlásení vyzve, aby si heslo zmenil.         │
                └───────────────────────────────────────────────────────────────┘
                """, DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }
}
