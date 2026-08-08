package org.javerlabd.homecenter.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.javerlabd.homecenter.stream.KnownLengthResource;
import org.javerlabd.homecenter.stream.MediaStream;
import org.javerlabd.homecenter.stream.MediaStreamService;
import org.javerlabd.homecenter.stream.RangeNotSatisfiableException;
import org.javerlabd.homecenter.user.UserService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies Range logic, not authentication. {@code @WithMockUser} only satisfies the
 * requirement that streams are accessible solely to authenticated clients.
 */
@WebMvcTest(StreamApiController.class)
@ActiveProfiles("test")
@WithMockUser
class StreamApiControllerTest {

    private static final byte[] CONTENT = "0123456789ABCDEFGHIJ".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaStreamService streamService;

    /** The web layer loads it through {@code PasswordChangeInterceptor}; this test does not use it. */
    @MockitoBean
    private UserService userService;

    @Test
    void bezRangeSaPosielaCelySuborS200() throws Exception {
        given(streamService.open(1L, null)).willReturn(stream(0, CONTENT.length, null));

        mockMvc.perform(get("/api/v1/media/1/stream"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, CONTENT.length))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(content().contentType("video/x-matroska"))
                .andExpect(content().bytes(CONTENT));
    }

    @Test
    void sRangeSaPosielaLenVyrezS206() throws Exception {
        given(streamService.open(1L, "bytes=10-14"))
                .willReturn(stream(10, 5, "bytes 10-14/" + CONTENT.length));

        mockMvc.perform(get("/api/v1/media/1/stream").header(HttpHeaders.RANGE, "bytes=10-14"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 10-14/20"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 5))
                .andExpect(content().bytes("ABCDE".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void rozsahMimoSuboruVrati416ajSkutocnuDlzku() throws Exception {
        given(streamService.open(1L, "bytes=9999-"))
                .willThrow(new RangeNotSatisfiableException(CONTENT.length));

        mockMvc.perform(get("/api/v1/media/1/stream").header(HttpHeaders.RANGE, "bytes=9999-"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */20"));
    }

    private static MediaStream stream(int start, int length, @Nullable String contentRange) {
        byte[] slice = Arrays.copyOfRange(CONTENT, start, start + length);
        KnownLengthResource resource = new KnownLengthResource(
                new ByteArrayInputStream(slice), length, "matrix.mkv");
        return new MediaStream(resource, "video/x-matroska", length, CONTENT.length,
                contentRange, "matrix.mkv");
    }
}
