package org.javerlabd.homecenter.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.javerlabd.homecenter.media.MediaService;
import org.javerlabd.homecenter.scan.ScanAlreadyRunningException;
import org.javerlabd.homecenter.scan.ScanService;
import org.javerlabd.homecenter.scan.ScanTrigger;
import org.javerlabd.homecenter.source.DuplicateSourceNameException;
import org.javerlabd.homecenter.source.NoActiveSourceException;
import org.javerlabd.homecenter.source.SmbAccessException;
import org.javerlabd.homecenter.source.SmbSource;
import org.javerlabd.homecenter.source.SmbSourceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Nastavenie Samba zdrojov. Sem patrí konfigurácia siete aj prihlasovacích údajov —
 * na diaľkovom ovládači sa to nastavovať nedá a ani nemá.
 *
 * <p>Zdrojov môže byť viac; každý sa dá samostatne vypnúť, preskenovať aj zmazať.
 */
@Controller
@RequestMapping("/admin/zdroje")
@RequiredArgsConstructor
public class SourceAdminController {

    private static final String LIST_VIEW = "zdroje";
    private static final String FORM_VIEW = "zdroj";

    private final SmbSourceService sourceService;
    private final MediaService mediaService;
    private final ScanService scanService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("sources", sourceService.findAll());
        model.addAttribute("usage", mediaService.usageBySource());
        model.addAttribute("lastScans", scanService.latestBySource());
        model.addAttribute("scanRunning", scanService.isRunning());
        return prepare(model, LIST_VIEW);
    }

    @GetMapping("/novy")
    public String create(Model model) {
        model.addAttribute("form", new SmbSourceForm());
        return prepare(model, FORM_VIEW);
    }

    @GetMapping("/{id}")
    public String edit(@PathVariable long id, Model model) {
        SmbSource source = sourceService.require(id);
        model.addAttribute("form", SmbSourceForm.from(source));
        model.addAttribute("usage", mediaService.usageOf(id));
        return prepare(model, FORM_VIEW);
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("form") SmbSourceForm form,
                       BindingResult binding,
                       Model model,
                       RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return prepare(model, FORM_VIEW);
        }
        try {
            SmbSource saved = sourceService.save(form.toSource());
            redirect.addFlashAttribute("success",
                    "Zdroj " + saved.name() + " je uložený. Spusti sken, nech sa načíta knižnica.");
            return "redirect:/admin/zdroje";
        } catch (DuplicateSourceNameException | SmbAccessException ex) {
            model.addAttribute("error", ex.getMessage());
            return prepare(model, FORM_VIEW);
        }
    }

    @PostMapping("/test")
    public String test(@Valid @ModelAttribute("form") SmbSourceForm form,
                       BindingResult binding,
                       Model model) {
        if (binding.hasErrors()) {
            return prepare(model, FORM_VIEW);
        }
        try {
            sourceService.verify(form.toSource());
            model.addAttribute("success", "Pripojenie funguje.");
        } catch (SmbAccessException ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return prepare(model, FORM_VIEW);
    }

    /** Sken jedného zdroja — vypnutý sa takto dá preskenovať zámerne. */
    @PostMapping("/{id}/sken")
    public String scan(@PathVariable long id, RedirectAttributes redirect) {
        try {
            scanService.triggerOne(id, ScanTrigger.MANUAL);
            redirect.addFlashAttribute("success",
                    "Sken zdroja " + sourceService.require(id).name() + " sa spustil.");
        } catch (NoActiveSourceException | ScanAlreadyRunningException | SmbAccessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/zdroje";
    }

    @PostMapping("/{id}/zmazat")
    public String delete(@PathVariable long id, RedirectAttributes redirect) {
        try {
            SmbSource source = sourceService.require(id);
            long items = mediaService.usageOf(id).items();
            sourceService.delete(id);
            redirect.addFlashAttribute("success", items == 0
                    ? "Zdroj " + source.name() + " je zmazaný."
                    : "Zdroj " + source.name() + " je zmazaný aj s " + items + " položkami indexu.");
        } catch (NoActiveSourceException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/zdroje";
    }

    private String prepare(Model model, String view) {
        model.addAttribute("active", "zdroje");
        return view;
    }
}
