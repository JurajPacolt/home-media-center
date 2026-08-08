package org.javerland.homecenter.admin;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.javerland.homecenter.media.MediaCategory;
import org.javerland.homecenter.media.MediaQuery;
import org.javerland.homecenter.media.MediaService;
import org.javerland.homecenter.source.SmbSourceService;
import org.javerland.homecenter.metadata.PosterResponse;
import org.javerland.homecenter.metadata.PosterStorage;
import org.javerland.homecenter.stream.MediaStreamResponse;
import org.javerland.homecenter.stream.MediaStreamService;
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

/** Browses the index to verify what the scan actually found. */
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
        // One query instead of looking up the source for every row.
        model.addAttribute("sourceNames", sourceService.namesById());
        model.addAttribute("search", search);
        model.addAttribute("total", total);
        model.addAttribute("page", currentPage);
        model.addAttribute("lastPage", lastPage);
        return "kniznica";
    }

    /**
     * Preview directly from the management UI. This address supplies the {@code <video>},
     * {@code <img>}, and {@code <audio>} elements in the preview dialog.
     *
     * <p>It has a dedicated address because {@code /api/v1/**} accepts only a Bearer token
     * from the TV client; a browser session would receive 401. The response uses the same
     * {@link MediaStreamResponse} as the REST API, including Range headers, so video seeking
     * behaves identically.
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
     * The same file, but as a download. The preview links to it for formats the browser
     * cannot play natively; the server intentionally does not transcode them.
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
