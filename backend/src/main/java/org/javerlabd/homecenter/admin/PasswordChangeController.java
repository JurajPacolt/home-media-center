package org.javerlabd.homecenter.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.javerlabd.homecenter.auth.AuthenticatedUser;
import org.javerlabd.homecenter.user.AppUser;
import org.javerlabd.homecenter.user.InvalidCredentialFormatException;
import org.javerlabd.homecenter.user.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Zmena vlastného hesla. Sem posiela {@link PasswordChangeInterceptor} každého, kto má
 * ešte predvolené heslo z prvého spustenia.
 */
@Controller
@RequestMapping("/admin/heslo")
@RequiredArgsConstructor
public class PasswordChangeController {

    private static final String VIEW = "heslo";

    private final UserService userService;

    @GetMapping
    public String form(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
        model.addAttribute("form", new PasswordChangeForm());
        return prepare(principal, model);
    }

    @PostMapping
    public String change(@Valid @ModelAttribute("form") PasswordChangeForm form,
                         BindingResult binding,
                         @AuthenticationPrincipal AuthenticatedUser principal,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return prepare(principal, model);
        }
        // Načerstvo z databázy — v session môže byť stav spred zmeny.
        AppUser user = userService.require(principal.user().requireId());

        if (!userService.passwordMatches(user, form.getCurrentPassword())) {
            model.addAttribute("error", "Súčasné heslo nesedí.");
            return prepare(principal, model);
        }
        if (!form.confirmationMatches()) {
            model.addAttribute("error", "Nové heslá sa nezhodujú.");
            return prepare(principal, model);
        }
        try {
            userService.changePassword(user.requireId(), form.getNewPassword());
        } catch (InvalidCredentialFormatException ex) {
            model.addAttribute("error", ex.getMessage());
            return prepare(principal, model);
        }
        redirect.addFlashAttribute("success",
                "Heslo je zmenené. Prihlásené televízory sa budú musieť prihlásiť znova.");
        return "redirect:/admin";
    }

    private String prepare(AuthenticatedUser principal, Model model) {
        model.addAttribute("active", "heslo");
        // Kým platí, stránka vysvetlí, prečo sa inam nedá prejsť.
        model.addAttribute("forced",
                userService.findById(principal.user().requireId())
                        .map(AppUser::mustChangePassword)
                        .orElse(false));
        return VIEW;
    }
}
