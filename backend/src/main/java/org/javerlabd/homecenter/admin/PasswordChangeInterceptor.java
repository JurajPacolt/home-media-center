package org.javerlabd.homecenter.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.javerlabd.homecenter.auth.AuthenticatedUser;
import org.javerlabd.homecenter.user.AppUser;
import org.javerlabd.homecenter.user.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Kým má správca predvolené heslo z prvého spustenia, pustí ho management UI jedine
 * na stránku zmeny hesla.
 *
 * <p>Príznak sa načítava z databázy, nie z prihlásenej relácie: po zmene hesla by
 * v session ostal starý stav a používateľ by tu uviazol v slučke.
 */
@Component
@RequiredArgsConstructor
public class PasswordChangeInterceptor implements HandlerInterceptor {

    static final String CHANGE_PATH = "/admin/heslo";

    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return true;
        }
        boolean mustChange = userService.findById(principal.user().requireId())
                .map(AppUser::mustChangePassword)
                .orElse(false);
        if (!mustChange) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + CHANGE_PATH);
        return false;
    }
}
