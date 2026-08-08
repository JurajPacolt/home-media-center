package org.javerlabd.homecenter.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.javerlabd.homecenter.user.Role;
import org.javerlabd.homecenter.user.UserDraft;
import org.javerlabd.homecenter.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Prihlásenie cez celý reťazec Spring Security — nie cez izolovaný controller.
 * Práve poskladanie reťazcov je to, čo sa dá ľahko pokaziť.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void users() {
        jdbc.sql("DELETE FROM auth_token").update();
        jdbc.sql("DELETE FROM app_user").update();
        userService.save(new UserDraft(null, "jano", "Jano", Role.USER,
                true, "tajneheslo123", "4321", false));
    }

    @Test
    void prihlasenieHeslomVratiToken() throws Exception {
        mockMvc.perform(login("jano", "tajneheslo123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("jano"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.hasPin").value(true));
    }

    @Test
    void prihlasenieFungujeAjPinom() throws Exception {
        mockMvc.perform(login("jano", "4321"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void nespravneUdajeVratia401BezNapovedy() throws Exception {
        mockMvc.perform(login("jano", "uplne-zle"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Nesprávne meno, heslo alebo PIN"));

        mockMvc.perform(login("nikto", "cokolvek"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Nesprávne meno, heslo alebo PIN"));
    }

    @Test
    void vydanyTokenOtvoriKniznicuAOdhlaseniemHoZneplatni() throws Exception {
        String token = tokenFor("jano", "tajneheslo123");

        mockMvc.perform(get("/api/v1/library").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/library").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointMeVratiPrihlasenehoPouzivatela() throws Exception {
        String token = tokenFor("jano", "4321");

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("jano"))
                .andExpect(jsonPath("$.displayName").value("Jano"));
    }

    /**
     * 403 musí prísť bez hlavičky {@code Location}. Keby ho reťazec poslal cez
     * {@code sendError}, spustil by ERROR dispatch na {@code /error} mimo {@code /api/v1/**}
     * a klient by dostal presmerovanie na prihlasovaciu stránku.
     */
    @Test
    void skenSmieSpustitLenSpravca() throws Exception {
        String userToken = tokenFor("jano", "tajneheslo123");

        mockMvc.perform(post("/api/v1/scan").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    private String tokenFor(String username, String secret) throws Exception {
        String body = mockMvc.perform(login(username, secret))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(body, "$.token");
        assertThat(token).isNotBlank();
        return token;
    }

    private org.springframework.test.web.servlet.RequestBuilder login(String username, String secret) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "secret": "%s", "deviceName": "Test"}
                        """.formatted(username, secret));
    }
}
