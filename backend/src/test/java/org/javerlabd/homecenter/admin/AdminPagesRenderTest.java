package org.javerlabd.homecenter.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.javerlabd.homecenter.auth.AuthenticatedUser;
import org.javerlabd.homecenter.media.MediaCategory;
import org.javerlabd.homecenter.media.MediaClassifier;
import org.javerlabd.homecenter.media.MediaItem;
import org.javerlabd.homecenter.media.MediaRepository;
import org.javerlabd.homecenter.source.SmbSource;
import org.javerlabd.homecenter.source.SmbSourceService;
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
 * Every management UI page must render successfully. An error in a Thymeleaf template
 * appears neither during compilation nor in service tests, only as a browser 500 response.
 *
 * <p>The data intentionally contains two sources and items from both: some templates
 * branch differently for a single source (the "Source" column and filter appear only
 * when two or more sources exist).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPagesRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private SmbSourceService sourceService;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private JdbcClient jdbc;

    private AppUser admin;
    private long prvyZdroj;
    private long druhyZdroj;

    @BeforeEach
    void data() {
        jdbc.sql("DELETE FROM media_item").update();
        jdbc.sql("DELETE FROM scan_run").update();
        jdbc.sql("DELETE FROM smb_source").update();
        jdbc.sql("DELETE FROM auth_token").update();
        jdbc.sql("DELETE FROM app_user").update();

        admin = userService.save(new UserDraft(null, "sef", "Šéf", Role.ADMIN,
                true, "tajneheslo123", null, false));
        prvyZdroj = sourceService.save(source("Filmy NAS", "192.168.1.10", "media")).requireId();
        druhyZdroj = sourceService.save(source("Archív", "192.168.1.20", "archiv")).requireId();
        mediaRepository.upsert(item(prvyZdroj, "filmy/matrix.mkv"), 1L);
        mediaRepository.upsert(item(druhyZdroj, "dovolenka/more.mkv"), 1L);
    }

    @Test
    void dashboardVyrenderujeVsetkyZdroje() throws Exception {
        render("/admin")
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Filmy NAS")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Archív")));
    }

    @Test
    void zoznamZdrojovSaVyrenderuje() throws Exception {
        render("/admin/zdroje")
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Filmy NAS")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Archív")));
    }

    @Test
    void formularNovehoAjExistujucehoZdrojaSaVyrenderuje() throws Exception {
        render("/admin/zdroje/novy");
        render("/admin/zdroje/" + prvyZdroj)
                .andExpect(content().string(org.hamcrest.Matchers.containsString("192.168.1.10")));
    }

    @Test
    void kniznicaSaVyrenderujeAjSFiltromZdroja() throws Exception {
        render("/admin/kniznica")
                .andExpect(content().string(org.hamcrest.Matchers.containsString("matrix")));
        render("/admin/kniznica?sourceId=" + druhyZdroj)
                .andExpect(content().string(org.hamcrest.Matchers.containsString("more")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("matrix"))));
    }

    /**
     * The preview relies on data-* attributes; without a category and Content-Type,
     * JavaScript would not know whether to insert video, image, or audio.
     */
    @Test
    void kniznicaNesieUdajePreNahlad() throws Exception {
        render("/admin/kniznica")
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-hc-preview")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-hc-category=\"VIDEO\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-hc-extension=\"mkv\"")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("data-hc-type=\"video/x-matroska\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"hc-preview\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"video-js vjs-big-play-centered\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/webjars/video.js/8.23.8/dist/video.min.js")));
    }

    @Test
    void videoJsSaServirujeLokalneZWebJaru() throws Exception {
        mockMvc.perform(get("/webjars/video.js/dist/video.min.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Video.js")));
    }

    @Test
    void pouzivateliaAHesloSaVyrenderuju() throws Exception {
        render("/admin/pouzivatelia");
        render("/admin/pouzivatelia/novy");
        render("/admin/pouzivatelia/" + admin.requireId());
        render("/admin/heslo");
    }

    @Test
    void prihlasovaciaStrankaSaVyrenderujeBezPrihlasenia() throws Exception {
        mockMvc.perform(get("/prihlasenie"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    private org.springframework.test.web.servlet.ResultActions render(String path) throws Exception {
        return mockMvc.perform(get(path).with(user(new AuthenticatedUser(admin))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    private static SmbSource source(String name, String host, String share) {
        return new SmbSource(null, name, host, 445, share, "", null, "juraj", "tajne",
                true, null, null);
    }

    private static MediaItem item(long sourceId, String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return new MediaItem(null, sourceId, MediaCategory.VIDEO, path, fileName,
                MediaClassifier.titleOf(fileName), MediaClassifier.extensionOf(fileName),
                1024, Instant.parse("2026-01-01T10:00:00Z"), "video/x-matroska", null, null, null);
    }
}
