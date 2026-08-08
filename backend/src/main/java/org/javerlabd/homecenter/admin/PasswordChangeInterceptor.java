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
 * While an administrator still has the default password from the first launch, the
 * management UI allows access only to the password change page.
 *
 * <p>The flag is loaded from the database, not the authenticated session: after a
 * password change, the session would retain the old state and trap the user in a loop.
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
