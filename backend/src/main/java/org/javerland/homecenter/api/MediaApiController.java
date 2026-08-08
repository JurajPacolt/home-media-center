package org.javerland.homecenter.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.javerland.homecenter.api.dto.LibrarySummaryDto;
import org.javerland.homecenter.api.dto.MediaItemDto;
import org.javerland.homecenter.api.dto.MediaGenreDto;
import org.javerland.homecenter.api.dto.MediaPageDto;
import org.javerland.homecenter.media.MediaCategory;
import org.javerland.homecenter.media.MediaQuery;
import org.javerland.homecenter.media.MediaService;
import org.javerland.homecenter.metadata.PosterResponse;
import org.javerland.homecenter.metadata.PosterStorage;
import org.javerland.homecenter.scan.ScanService;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

/** Reads the library for the Android TV client. Everything comes from the index; Samba is not accessed here. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Library", description = "Overview and listing of indexed media")
@RequiredArgsConstructor
public class MediaApiController {

    private final MediaService mediaService;
    private final ScanService scanService;
    private final PosterStorage posterStorage;

    @GetMapping("/library")
    @Operation(summary = "Library overview—the counts behind the Videos / Photos / Music tiles")
    public LibrarySummaryDto library() {
        return LibrarySummaryDto.from(mediaService.summary(), scanService.latest().orElse(null));
    }

    @GetMapping("/media")
    @Operation(summary = "Lists media, optionally filtered by category, source, genre and search text")
    public MediaPageDto list(@RequestParam(required = false) @Nullable MediaCategory category,
                             @RequestParam(required = false) @Nullable Long sourceId,
                             @RequestParam(required = false) @Nullable Long genreId,
                             @RequestParam(required = false) @Nullable String search,
                             @RequestParam(defaultValue = "60") int limit,
                             @RequestParam(defaultValue = "0") int offset) {
        MediaQuery query = new MediaQuery(category, sourceId, genreId, search, limit, offset);
        List<MediaItemDto> items = mediaService.find(query).stream().map(MediaItemDto::from).toList();
        return new MediaPageDto(items, mediaService.count(query), query.limit(), query.offset());
    }

    @GetMapping("/media/{id}")
    @Operation(summary = "Details of a single item")
    public MediaItemDto detail(@PathVariable long id) {
        return MediaItemDto.from(mediaService.require(id));
    }

    @GetMapping("/genres")
    @Operation(summary = "Movie genres currently in use in the library")
    public List<MediaGenreDto> genres() {
        return mediaService.genres().stream().map(MediaGenreDto::from).toList();
    }

    @GetMapping("/media/{id}/poster")
    @Operation(summary = "Locally cached poster or episode still")
    public ResponseEntity<Resource> poster(@PathVariable long id) {
        return PosterResponse.of(posterStorage.open(mediaService.require(id)));
    }
}
