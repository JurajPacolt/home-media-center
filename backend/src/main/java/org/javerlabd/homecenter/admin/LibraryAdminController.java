package org.javerlabd.homecenter.admin;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.javerlabd.homecenter.media.MediaCategory;
import org.javerlabd.homecenter.media.MediaQuery;
import org.javerlabd.homecenter.media.MediaService;
import org.javerlabd.homecenter.source.SmbSourceService;
import org.javerlabd.homecenter.metadata.PosterResponse;
import org.javerlabd.homecenter.metadata.PosterStorage;
import org.javerlabd.homecenter.stream.MediaStreamResponse;
import org.javerlabd.homecenter.stream.MediaStreamService;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** Prehliadanie indexu — kontrola, čo sken naozaj našiel. */
@Controller
@RequestMapping("/admin/kniznica")
@RequiredArgsConstructor
public class LibraryAdminController {

    private static final int PAGE_SIZE = 50;

    private final MediaService mediaService;
    private final MediaStreamService streamService;
    private final SmbSourceService sourceService;
    private final PosterStorage posterStorage;

    @GetMapping
    public String library(@RequestParam(required = false) @Nullable MediaCategory category,
                          @RequestParam(required = false) @Nullable Long sourceId,
                          @RequestParam(required = false) @Nullable Long genreId,
                          @RequestParam(required = false) @Nullable String search,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        int currentPage = Math.max(page, 0);
        MediaQuery query = new MediaQuery(category, sourceId, genreId, search,
                PAGE_SIZE, currentPage * PAGE_SIZE);
        long total = mediaService.count(query);
        int lastPage = total == 0 ? 0 : (int) ((total - 1) / PAGE_SIZE);

        model.addAttribute("active", "kniznica");
        model.addAttribute("items", mediaService.find(query));
        model.addAttribute("categories", MediaCategory.values());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("sources", sourceService.findAll());
        model.addAttribute("selectedSourceId", sourceId);
        model.addAttribute("genres", mediaService.genres());
        model.addAttribute("selectedGenreId", genreId);
        // Jeden dotaz namiesto vyhľadávania zdroja ku každému riadku.
        model.addAttribute("sourceNames", sourceService.namesById());
        model.addAttribute("search", search);
        model.addAttribute("total", total);
        model.addAttribute("page", currentPage);
        model.addAttribute("lastPage", lastPage);
        return "kniznica";
    }

    /**
     * Náhľad priamo z management UI — z tejto adresy sa plní {@code <video>},
     * {@code <img>} aj {@code <audio>} v okne náhľadu.
     *
     * <p>Vlastnú adresu má preto, že {@code /api/v1/**} prijíma len Bearer token od TV
     * klienta — prehliadač so session by dostal 401. Odpoveď skladá rovnaký
     * {@link MediaStreamResponse} ako REST API vrátane Range hlavičiek, takže sa
     * pretáčanie vo videu chová rovnako.
     */
    @GetMapping("/{id}/stream")
    @ResponseBody
    public ResponseEntity<Resource> stream(
            @PathVariable long id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) @Nullable String range,
            HttpServletRequest request) {

        return MediaStreamResponse.of(
                streamService.open(id, range),
                HttpMethod.HEAD.matches(request.getMethod()));
    }

    /**
     * Ten istý súbor, ale na uloženie. Náhľad naň odkazuje pri formátoch, ktoré
     * prehliadač natívne neprehrá — server ich zámerne netranskóduje.
     */
    @GetMapping("/{id}/stiahnut")
    @ResponseBody
    public ResponseEntity<Resource> download(
            @PathVariable long id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) @Nullable String range,
            HttpServletRequest request) {

        return MediaStreamResponse.ofDownload(
                streamService.open(id, range),
                HttpMethod.HEAD.matches(request.getMethod()));
    }

    @GetMapping("/{id}/poster")
    @ResponseBody
    public ResponseEntity<Resource> poster(@PathVariable long id) {
        return PosterResponse.of(posterStorage.open(mediaService.require(id)));
    }
}
