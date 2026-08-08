package org.javerland.homecenter.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.javerland.homecenter.support.Timestamps;
import org.javerland.homecenter.user.AppUser;
import org.javerland.homecenter.user.Role;
import org.javerland.homecenter.user.UserDraft;
import org.javerland.homecenter.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AuthTokenServiceTest {

    @Autowired
    private AuthTokenService tokenService;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcClient jdbc;

    private AppUser user;

    @BeforeEach
    void freshUser() {
        jdbc.sql("DELETE FROM auth_token").update();
        jdbc.sql("DELETE FROM app_user").update();
        user = userService.save(new UserDraft(null, "jano", "Jano", Role.USER,
                true, "tajneheslo123", "4321", false));
    }

    @Test
    void vydanyTokenSaDaPreloziNaPouzivatela() {
        IssuedToken issued = tokenService.issue(user, "Obývačka");

        assertThat(issued.token()).isNotBlank();
        assertThat(issued.expiresAt()).isAfter(Instant.now());
        assertThat(tokenService.resolve(issued.token()))
                .get()
                .extracting(AppUser::username)
                .isEqualTo("jano");
    }

    @Test
    void doDatabazySaUkladaLenHashNieSamotnyToken() {
        IssuedToken issued = tokenService.issue(user, null);

        String storedHash = jdbc.sql("SELECT token_hash FROM auth_token").query(String.class).single();
        assertThat(storedHash)
                .hasSize(64)
                .isNotEqualTo(issued.token());
    }

    @Test
    void neznamyTokenNeprejde() {
        assertThat(tokenService.resolve("vymysleny-token")).isEmpty();
    }

    @Test
    void expirovanyTokenNeprejdeAZmizne() {
        IssuedToken issued = tokenService.issue(user, null);
        jdbc.sql("UPDATE auth_token SET expires_at = :past")
                .param("past", Timestamps.toDatabase(Instant.now().minusSeconds(60)))
                .update();

        assertThat(tokenService.resolve(issued.token())).isEmpty();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM auth_token").query(Long.class).single())
                .as("expirovaný token sa pri pokuse o použitie rovno upratal")
                .isZero();
    }

    @Test
    void odhlasenieZneplatniToken() {
        IssuedToken issued = tokenService.issue(user, null);

        tokenService.revoke(issued.token());

        assertThat(tokenService.resolve(issued.token())).isEmpty();
    }

    @Test
    void zmenaHeslaOdhlasiVsetkyZariadenia() {
        IssuedToken tv = tokenService.issue(user, "Obývačka");
        IssuedToken spalna = tokenService.issue(user, "Spálňa");

        userService.changePassword(user.requireId(), "uplnenoveheslo");

        assertThat(tokenService.resolve(tv.token())).isEmpty();
        assertThat(tokenService.resolve(spalna.token())).isEmpty();
    }

    @Test
    void zmenaPinuTiezOdhlasiZariadenia() {
        IssuedToken tv = tokenService.issue(user, "Obývačka");

        userService.save(new UserDraft(user.requireId(), "jano", "Jano", Role.USER,
                true, null, "9999", false));

        assertThat(tokenService.resolve(tv.token())).isEmpty();
    }

    @Test
    void vypnutyUcetZneplatniToken() {
        IssuedToken tv = tokenService.issue(user, "Obývačka");

        userService.save(new UserDraft(user.requireId(), "jano", "Jano", Role.USER,
                false, null, null, false));

        assertThat(tokenService.resolve(tv.token())).isEmpty();
    }

    @Test
    void zmazanyPouzivatelSiSoSebouZoberieTokeny() {
        tokenService.issue(user, "Obývačka");

        jdbc.sql("DELETE FROM app_user WHERE id = :id").param("id", user.requireId()).update();

        assertThat(jdbc.sql("SELECT COUNT(*) FROM auth_token").query(Long.class).single())
                .as("ON DELETE CASCADE v schéme")
                .isZero();
    }

    @Test
    void upratovanieOdstraniLenExpirovane() {
        IssuedToken platny = tokenService.issue(user, "Platný");
        tokenService.issue(user, "Starý");
        jdbc.sql("UPDATE auth_token SET expires_at = :past WHERE device_name = 'Starý'")
                .param("past", Timestamps.toDatabase(Instant.now().minusSeconds(60)))
                .update();

        tokenService.purgeExpired();

        assertThat(tokenService.resolve(platny.token())).isPresent();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM auth_token").query(Long.class).single()).isEqualTo(1);
    }
}
