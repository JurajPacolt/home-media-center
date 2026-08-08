package org.javerlabd.homecenter.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Prvé spustenie: bez účtu by sa do management UI nedalo dostať vôbec, takže server
 * založí správcu {@code admin} s heslom {@code admin} a označí ho príznakom vynútenej
 * zmeny hesla — {@code PasswordChangeInterceptor} ho potom nikam inam nepustí.
 *
 * <p>Ide zámerne mimo {@link UserService#save(UserDraft)}: predvolené heslo je kratšie,
 * než by validácia pripustila, a to je v poriadku práve preto, že sa musí hneď zmeniť.
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
