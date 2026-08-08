package org.javerlabd.homecenter.auth;

import lombok.RequiredArgsConstructor;
import org.javerlabd.homecenter.user.UserService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Načítanie účtu pre prihlásenie do management UI. PIN sa tadiaľto nedostane —
 * {@code DaoAuthenticationProvider} porovnáva výhradne hash hesla. PIN je vyhradený
 * pre REST API a rieši ho {@code AuthApiController}.
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserService userService;

    @Override
    public AuthenticatedUser loadUserByUsername(String username) {
        return userService.findByUsername(username)
                .map(AuthenticatedUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("Používateľ '" + username + "' neexistuje"));
    }
}
