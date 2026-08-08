package org.javerlabd.homecenter.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.javerlabd.homecenter.auth.AuthenticatedUser;
import org.javerlabd.homecenter.user.AppUser;
import org.javerlabd.homecenter.user.Role;
import org.javerlabd.homecenter.user.UserDraft;
import org.javerlabd.homecenter.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Kto sa kam dostane. Rozdelenie na dva reťazce (bezstavové API vs. session UI) je
 * presne to miesto, kde sa chyba prejaví až v prevádzke.
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
     * Zlyhaný CSRF token je tiež {@code AccessDeniedException}. Nesmie skončiť tichým
     * odhlásením a hláškou o tom, že účet patrí televízoru — správca by nechápal, čo sa deje.
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
