package org.javerlabd.homecenter.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.javerlabd.homecenter.auth.AuthTokenService;
import org.javerlabd.homecenter.auth.AuthenticatedUser;
import org.javerlabd.homecenter.user.AppUser;
import org.javerlabd.homecenter.user.DuplicateUsernameException;
import org.javerlabd.homecenter.user.InvalidCredentialFormatException;
import org.javerlabd.homecenter.user.LastAdminException;
import org.javerlabd.homecenter.user.Role;
import org.javerlabd.homecenter.user.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * User management. This is a thin layer over {@code UserService}; hashing, validation,
 * and the last-administrator safeguard belong in the service, not here.
 */
@Controller
@RequestMapping("/admin/pouzivatelia")
@RequiredArgsConstructor
public class UserAdminController {

    private static final String LIST_VIEW = "pouzivatelia";
    private static final String FORM_VIEW = "pouzivatel";

    private final UserService userService;
    private final AuthTokenService tokenService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        return prepare(model, LIST_VIEW);
    }

    @GetMapping("/novy")
    public String create(Model model) {
        model.addAttribute("form", new UserForm());
        return prepare(model, FORM_VIEW);
    }

    @GetMapping("/{id}")
    public String edit(@PathVariable long id, Model model) {
        AppUser user = userService.require(id);
        model.addAttribute("form", UserForm.from(user));
        model.addAttribute("devices", tokenService.devicesOf(id));
        return prepare(model, FORM_VIEW);
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("form") UserForm form,
                       BindingResult binding,
                       Model model,
                       RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return prepare(model, FORM_VIEW);
        }
        try {
            AppUser saved = userService.save(form.toDraft());
            redirect.addFlashAttribute("success", "Používateľ " + saved.username() + " je uložený.");
            return "redirect:/admin/pouzivatelia";
        } catch (DuplicateUsernameException | InvalidCredentialFormatException | LastAdminException ex) {
            model.addAttribute("error", ex.getMessage());
            return prepare(model, FORM_VIEW);
        }
    }

    @PostMapping("/{id}/zmazat")
    public String delete(@PathVariable long id,
                         @AuthenticationPrincipal AuthenticatedUser principal,
                         RedirectAttributes redirect) {
        if (principal.user().requireId() == id) {
            redirect.addFlashAttribute("error", "Vlastný účet zmazať nemôžeš.");
            return "redirect:/admin/pouzivatelia";
        }
        try {
            userService.delete(id);
            redirect.addFlashAttribute("success", "Používateľ je zmazaný.");
        } catch (LastAdminException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/pouzivatelia";
    }

    /** Logs out all TVs for the given user, for example after a device is lost. */
    @PostMapping("/{id}/odhlasit")
    public String revokeDevices(@PathVariable long id, RedirectAttributes redirect) {
        int revoked = tokenService.revokeAllFor(userService.require(id).requireId());
        redirect.addFlashAttribute("success", revoked == 0
                ? "Žiadne prihlásené zariadenia."
                : "Odhlásených zariadení: " + revoked + ".");
        return "redirect:/admin/pouzivatelia/" + id;
    }

    private String prepare(Model model, String view) {
        model.addAttribute("active", "pouzivatelia");
        model.addAttribute("roles", Role.values());
        return view;
    }
}
