package org.javerland.homecenter.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
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
 * Each provider has different attribution obligations — TMDB requires an exact sentence and
 * their logo, Cinemeta requires nothing. The library must therefore credit the provider a scan
 * would actually use, not whichever one happens to be in the template.
 */
@SpringBootTest(properties = "homecenter.metadata.cinemeta-fallback=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LibraryAttributionRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcClient jdbc;

    private AppUser admin;

    @BeforeEach
    void data() {
        jdbc.sql("DELETE FROM auth_token").update();
        jdbc.sql("DELETE FROM app_user").update();
        admin = userService.save(new UserDraft(null, "sef", "Šéf", Role.ADMIN,
                true, "tajneheslo123", null, false));
    }

    @Test
    void bezTokenuTmdbSaUvedieCinemeta() throws Exception {
        mockMvc.perform(get("/admin/kniznica").with(user(new AuthenticatedUser(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Cinemeta")))
                .andExpect(content().string(Matchers.not(
                        Matchers.containsString("not endorsed or certified by TMDB"))));
    }
}
