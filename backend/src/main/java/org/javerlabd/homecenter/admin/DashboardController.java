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

/** Úvodná obrazovka management UI: stav knižnice, zdrojov a skenov. */
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
        // História mieša behy viacerých zdrojov, treba pri nich ukázať, ktorého sa týkajú.
        model.addAttribute("sourceNames", sourceService.namesById());
        model.addAttribute("scanRunning", scanService.isRunning());
        model.addAttribute("lastScan", scanService.latest().orElse(null));
        model.addAttribute("history", scanService.history(10));
        return "dashboard";
    }

    /**
     * Priebeh skenu pre dashboard. Vlastný endpoint má preto, že {@code /api/v1/**} je
     * bezstavové a prijíma výhradne Bearer token — prehliadač so session by tam neprešiel.
     * Logika ostáva spoločná, líši sa len spôsob prihlásenia.
     */
    @GetMapping("/admin/sken/stav")
    @ResponseBody
    public ResponseEntity<ScanRunDto> scanStatus() {
        return scanService.latest()
                .map(run -> ResponseEntity.ok(ScanRunDto.from(run)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Sken všetkých zapnutých zdrojov. Jeden zdroj sa spúšťa z prehľadu zdrojov. */
    @PostMapping("/admin/sken")
    public String startScan(RedirectAttributes redirect) {
        try {
            ScanStart started = scanService.triggerAll(ScanTrigger.MANUAL);
            // Bez číslovky pred podstatným menom — slovenčina by za ňou striedala pád.
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
