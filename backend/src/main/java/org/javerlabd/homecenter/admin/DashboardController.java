package org.javerlabd.homecenter.admin;

import lombok.RequiredArgsConstructor;
import org.javerlabd.homecenter.api.dto.ScanRunDto;
import org.javerlabd.homecenter.media.MediaService;
import org.javerlabd.homecenter.scan.ScanAlreadyRunningException;
import org.javerlabd.homecenter.scan.ScanService;
import org.javerlabd.homecenter.scan.ScanStart;
import org.javerlabd.homecenter.scan.ScanTrigger;
import org.javerlabd.homecenter.source.NoActiveSourceException;
import org.javerlabd.homecenter.source.SmbAccessException;
import org.javerlabd.homecenter.source.SmbSourceService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Management UI dashboard: library, source, and scan status. */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final MediaService mediaService;
    private final SmbSourceService sourceService;
    private final ScanService scanService;

    @GetMapping("/")
    public String root() {
        return "redirect:/admin";
    }

    @GetMapping("/admin")
    public String dashboard(Model model) {
        model.addAttribute("active", "dashboard");
        model.addAttribute("summary", mediaService.summary());
        model.addAttribute("sources", sourceService.findAll());
        model.addAttribute("usage", mediaService.usageBySource());
        model.addAttribute("lastScans", scanService.latestBySource());
        // The history combines runs for multiple sources, so each run must identify its source.
        model.addAttribute("sourceNames", sourceService.namesById());
        model.addAttribute("scanRunning", scanService.isRunning());
        model.addAttribute("lastScan", scanService.latest().orElse(null));
        model.addAttribute("history", scanService.history(10));
        return "dashboard";
    }

    /**
     * Scan progress for the dashboard. It has a dedicated endpoint because {@code /api/v1/**}
     * is stateless and accepts only a Bearer token; a browser session would not be accepted.
     * The logic remains shared; only the authentication method differs.
     */
    @GetMapping("/admin/sken/stav")
    @ResponseBody
    public ResponseEntity<ScanRunDto> scanStatus() {
        return scanService.latest()
                .map(run -> ResponseEntity.ok(ScanRunDto.from(run)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Scans all enabled sources. A single source is started from the source overview. */
    @PostMapping("/admin/sken")
    public String startScan(RedirectAttributes redirect) {
        try {
            ScanStart started = scanService.triggerAll(ScanTrigger.MANUAL);
            // No numeral before the noun because Slovak would change its grammatical case.
            redirect.addFlashAttribute("success", started.count() == 1
                    ? "Sken sa spustil, priebeh sa dopĺňa nižšie."
                    : "Sken sa spustil, zdroje sa prechádzajú za sebou: "
                            + String.join(", ", started.sources()) + ".");
        } catch (NoActiveSourceException | ScanAlreadyRunningException | SmbAccessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin";
    }
}
