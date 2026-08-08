package org.javerland.homecenter.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Management UI login page. Spring Security performs authentication through POST at
 * the same address; this controller only renders the form and messages.
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
            // Authentication succeeded, but the USER role belongs exclusively to the TV client.
            model.addAttribute("error",
                    "Tento účet je určený len pre televízor — do správy servera nemá prístup.");
        } else if (odhlasene != null) {
            model.addAttribute("success", "Odhlásenie prebehlo.");
        }
        return "prihlasenie";
    }
}
