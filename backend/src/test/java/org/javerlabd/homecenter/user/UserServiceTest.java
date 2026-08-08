package org.javerlabd.homecenter.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs against real H2 and Argon2 implementations because hashing is the behavior worth
 * verifying here; a mocked encoder would make the test meaningless.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcClient jdbc;

    /** The context is shared between tests; {@code UserBootstrap} has already created the administrator. */
    @BeforeEach
    void clearUsers() {
        jdbc.sql("DELETE FROM auth_token").update();
        jdbc.sql("DELETE FROM app_user").update();
    }

    @Test
    void hesloSaUkladaAkoArgon2HashNieOtvorene() {
        AppUser saved = userService.save(draft("jano", "tajneheslo123", null));

        assertThat(saved.passwordHash())
                .as("Argon2id má vlastnú predponu, soľ je súčasťou reťazca")
                .startsWith("$argon2id$")
                .doesNotContain("tajneheslo123");
        assertThat(saved.toString()).doesNotContain("tajneheslo123");
    }

    @Test
    void prihlasenieFungujeHeslomAjPinom() {
        userService.save(draft("jano", "tajneheslo123", "4321"));

        assertThat(userService.authenticate("jano", "tajneheslo123", true)).isPresent();
        assertThat(userService.authenticate("jano", "4321", true)).isPresent();
        assertThat(userService.authenticate("jano", "0000", true)).isEmpty();
    }

    @Test
    void menoJeNecitliveNaVelkostPismen() {
        userService.save(draft("jano", "tajneheslo123", null));

        assertThat(userService.authenticate("JANO", "tajneheslo123", true)).isPresent();
        assertThat(userService.authenticate("  Jano ", "tajneheslo123", true)).isPresent();
    }

    @Test
    void pinNeplatiTamKdeHoVolajuciNepovoli() {
        userService.save(draft("jano", "tajneheslo123", "4321"));

        assertThat(userService.authenticate("jano", "4321", false))
                .as("management UI vyžaduje plné heslo")
                .isEmpty();
        assertThat(userService.authenticate("jano", "tajneheslo123", false)).isPresent();
    }

    @Test
    void vypnutyUcetSaNeprihlasiAniSpravnymHeslom() {
        AppUser user = userService.save(draft("jano", "tajneheslo123", null));
        userService.save(new UserDraft(user.requireId(), "jano", "Jano", Role.USER,
                false, null, null, false));

        assertThat(userService.authenticate("jano", "tajneheslo123", true)).isEmpty();
    }

    @Test
    void neznamePouzivatelskeMenoNeprejde() {
        assertThat(userService.authenticate("nikto", "cokolvek", true)).isEmpty();
    }

    @Test
    void duplicitneMenoSaOdmietne() {
        userService.save(draft("jano", "tajneheslo123", null));

        assertThatThrownBy(() -> userService.save(draft("JANO", "ineheslo1234", null)))
                .isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    void kratkeHesloAKratkyPinNeprejdu() {
        assertThatThrownBy(() -> userService.save(draft("jano", "kratke", null)))
                .isInstanceOf(InvalidCredentialFormatException.class)
                .hasMessageContaining("8");

        assertThatThrownBy(() -> userService.save(draft("jano", "tajneheslo123", "12")))
                .isInstanceOf(InvalidCredentialFormatException.class)
                .hasMessageContaining("PIN");

        assertThatThrownBy(() -> userService.save(draft("jano", "tajneheslo123", "12ab")))
                .as("PIN sú výhradne číslice")
                .isInstanceOf(InvalidCredentialFormatException.class);
    }

    @Test
    void prazdneHesloPriUpraveNechavaPovodne() {
        AppUser saved = userService.save(draft("jano", "tajneheslo123", null));

        AppUser updated = userService.save(new UserDraft(saved.requireId(), "jano", "Jano Novy",
                Role.USER, true, "", null, false));

        assertThat(updated.displayName()).isEqualTo("Jano Novy");
        assertThat(updated.passwordHash()).isEqualTo(saved.passwordHash());
        assertThat(userService.authenticate("jano", "tajneheslo123", true)).isPresent();
    }

    @Test
    void zrusenyPinUzNeplatiAleHesloAno() {
        AppUser saved = userService.save(draft("jano", "tajneheslo123", "4321"));

        AppUser updated = userService.save(new UserDraft(saved.requireId(), "jano", "Jano",
                Role.USER, true, null, null, true));

        assertThat(updated.hasPin()).isFalse();
        assertThat(userService.authenticate("jano", "4321", true)).isEmpty();
        assertThat(userService.authenticate("jano", "tajneheslo123", true)).isPresent();
    }

    @Test
    void poslednehoZapnutehoSpravcuNejdeZmazatVypnutAniPreradit() {
        AppUser admin = userService.save(new UserDraft(null, "sef", "Šéf", Role.ADMIN,
                true, "tajneheslo123", null, false));
        long id = admin.requireId();

        assertThatThrownBy(() -> userService.delete(id))
                .isInstanceOf(LastAdminException.class);
        assertThatThrownBy(() -> userService.save(new UserDraft(id, "sef", "Šéf", Role.USER,
                true, null, null, false)))
                .as("preradenie na USER by zamklo management UI")
                .isInstanceOf(LastAdminException.class);
        assertThatThrownBy(() -> userService.save(new UserDraft(id, "sef", "Šéf", Role.ADMIN,
                false, null, null, false)))
                .as("vypnutie posledného správcu má rovnaký následok")
                .isInstanceOf(LastAdminException.class);
    }

    @Test
    void druhySpravcaUvolniPoistkuNaPosledneho() {
        AppUser first = userService.save(new UserDraft(null, "sef", "Šéf", Role.ADMIN,
                true, "tajneheslo123", null, false));
        userService.save(new UserDraft(null, "sef2", "Druhý", Role.ADMIN,
                true, "tajneheslo123", null, false));

        userService.delete(first.requireId());

        assertThat(userService.findAll()).extracting(AppUser::username).containsExactly("sef2");
    }

    @Test
    void zmenaHeslaZhasneVynutenuZmenu() {
        AppUser saved = userService.save(draft("jano", "tajneheslo123", null));
        jdbc.sql("UPDATE app_user SET must_change_password = TRUE WHERE id = :id")
                .param("id", saved.requireId())
                .update();

        AppUser changed = userService.changePassword(saved.requireId(), "noveheslo123");

        assertThat(changed.mustChangePassword()).isFalse();
        assertThat(userService.authenticate("jano", "noveheslo123", true)).isPresent();
        assertThat(userService.authenticate("jano", "tajneheslo123", true)).isEmpty();
    }

    private static UserDraft draft(String username, String password, String pin) {
        return new UserDraft(null, username, username, Role.USER, true, password, pin, false);
    }
}
