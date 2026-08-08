package org.javerland.homecenter.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.javerland.homecenter.stream.MediaStreamResponse;
import org.javerland.homecenter.stream.MediaStreamService;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proxies a file from Samba. Range request support is mandatory because video seeking
 * does not work without it.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Playback", description = "Transfers files from Samba with Range request support")
@RequiredArgsConstructor
public class StreamApiController {

    private final MediaStreamService streamService;

    @GetMapping("/media/{id}/stream")
    @Operation(summary = "Plays a file; with a Range header it returns 206 and the requested part")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<Resource> stream(
            @PathVariable long id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) @Nullable String range,
            HttpServletRequest request) {

        return MediaStreamResponse.of(
                streamService.open(id, range),
                HttpMethod.HEAD.matches(request.getMethod()));
    }
}
