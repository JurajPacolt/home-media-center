package org.javerland.homecenter.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.javerland.homecenter.auth.AuthenticatedUser;
import org.javerland.homecenter.user.AppUser;
import org.javerland.homecenter.user.Role;
import org.javerland.homecenter.user.UserDraft;
import org.javerland.homecenter.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies who can access which area. The two-chain separation (stateless API versus
 * session UI) is precisely where an error may appear only in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityRulesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcClient jdbc;

    private AppUser admin;
    private AppUser viewer;

    @BeforeEach
    void users() {
        jdbc.sql("DELETE FROM auth_token").update();
        jdbc.sql("DELETE FROM app_user").update();
        admin = userService.save(new UserDraft(null, "sef", "Šéf", Role.ADMIN,
                true, "tajneheslo123", null, false));
        viewer = userService.save(new UserDraft(null, "jano", "Jano", Role.USER,
                true, "tajneheslo123", "4321", false));
    }

    @Test
    void neprihlaseneUiPresmerujeNaPrihlasenie() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/prihlasenie"));
    }

    @Test
    void prihlasovaciaStrankaAJejStatikaSuVerejne() throws Exception {
        mockMvc.perform(get("/prihlasenie")).andExpect(status().isOk());
        mockMvc.perform(get("/css/homecenter.css")).andExpect(status().isOk());
    }

    @Test
    void spravcaSaDoUiDostane() throws Exception {
        mockMvc.perform(get("/admin").with(user(new AuthenticatedUser(admin))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/pouzivatelia").with(user(new AuthenticatedUser(admin))))
                .andExpect(status().isOk());
    }

    @Test
    void pouzivatelSRolouUserDoUiNesmie() throws Exception {
        mockMvc.perform(get("/admin").with(user(new AuthenticatedUser(viewer))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/prihlasenie?rola"));
    }

    /**
     * A failed CSRF token also produces {@code AccessDeniedException}. It must not cause a
     * silent logout and a message that the account belongs to the TV; an administrator
     * would not understand what happened.
     */
    @Test
    void chybajuciCsrfTokenJe403NieOdhlasenie() throws Exception {
        mockMvc.perform(post("/admin/sken").with(user(new AuthenticatedUser(admin))))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void spravnyCsrfTokenPrejde() throws Exception {
        mockMvc.perform(post("/admin/sken").with(user(new AuthenticatedUser(admin))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void apiBezTokenuVrati401NieHtmlPresmerovanie() throws Exception {
        mockMvc.perform(get("/api/v1/library"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void neplatnyTokenJeToSamoAkoZiadny() throws Exception {
        mockMvc.perform(get("/api/v1/library").header("Authorization", "Bearer nezmysel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openApiSpecNieJeVerejna() throws Exception {
        mockMvc.perform(get("/api/openapi"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/api/openapi").with(user(new AuthenticatedUser(admin))))
                .andExpect(status().isOk());
    }

    @Test
    void healthOstavaVerejnyKvoliMonitoringu() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
