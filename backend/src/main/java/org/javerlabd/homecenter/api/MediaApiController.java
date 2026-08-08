package org.javerlabd.homecenter.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.javerlabd.homecenter.api.dto.LibrarySummaryDto;
import org.javerlabd.homecenter.api.dto.MediaItemDto;
import org.javerlabd.homecenter.api.dto.MediaGenreDto;
import org.javerlabd.homecenter.api.dto.MediaPageDto;
import org.javerlabd.homecenter.media.MediaCategory;
import org.javerlabd.homecenter.media.MediaQuery;
import org.javerlabd.homecenter.media.MediaService;
import org.javerlabd.homecenter.metadata.PosterResponse;
import org.javerlabd.homecenter.metadata.PosterStorage;
import org.javerlabd.homecenter.scan.ScanService;
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
@Tag(name = "Knižnica", description = "Prehľad a výpis indexovaných médií")
@RequiredArgsConstructor
public class MediaApiController {

    private final MediaService mediaService;
    private final ScanService scanService;
    private final PosterStorage posterStorage;

    @GetMapping("/library")
    @Operation(summary = "Prehľad knižnice — počty pre dlaždice Videá / Fotky / Hudba")
    public LibrarySummaryDto library() {
        return LibrarySummaryDto.from(mediaService.summary(), scanService.latest().orElse(null));
    }

    @GetMapping("/media")
    @Operation(summary = "Výpis médií, voliteľne filtrovaný podľa kategórie, zdroja a hľadaného textu")
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
    @Operation(summary = "Detail jednej položky")
    public MediaItemDto detail(@PathVariable long id) {
        return MediaItemDto.from(mediaService.require(id));
    }

    @GetMapping("/genres")
    @Operation(summary = "Filmové žánre, ktoré sa v knižnici aktuálne používajú")
    public List<MediaGenreDto> genres() {
        return mediaService.genres().stream().map(MediaGenreDto::from).toList();
    }

    @GetMapping("/media/{id}/poster")
    @Operation(summary = "Lokálne cachovaný plagát alebo náhľad epizódy")
    public ResponseEntity<Resource> poster(@PathVariable long id) {
        return PosterResponse.of(posterStorage.open(mediaService.require(id)));
    }
}
