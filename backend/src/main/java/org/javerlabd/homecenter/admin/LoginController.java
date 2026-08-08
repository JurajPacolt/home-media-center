package org.javerlabd.homecenter.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Prihlasovacia stránka management UI. Samotné overenie robí Spring Security na
 * rovnakej adrese cez POST — tento controller len vykresľuje formulár a hlášky.
 */
@Controller
public class LoginController {

    @GetMapping("/prihlasenie")
    public String login(@RequestParam(required = false) String chyba,
                        @RequestParam(required = false) String rola,
                        @RequestParam(required = false) String odhlasene,
                        Model model) {
        if (chyba != null) {
            model.addAttribute("error", "Nesprávne meno alebo heslo.");
        } else if (rola != null) {
            // Prihlásenie prešlo, ale rola USER patrí výhradne do TV klienta.
            model.addAttribute("error",
                    "Tento účet je určený len pre televízor — do správy servera nemá prístup.");
        } else if (odhlasene != null) {
            model.addAttribute("success", "Odhlásenie prebehlo.");
        }
        return "prihlasenie";
    }
}
