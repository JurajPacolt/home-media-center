package org.javerlabd.homecenter.auth;

import lombok.RequiredArgsConstructor;
import org.javerlabd.homecenter.user.UserService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads an account for management UI login. A PIN never passes through here;
 * {@code DaoAuthenticationProvider} compares only the password hash. PIN handling is
 * reserved for the REST API and performed by {@code AuthApiController}.
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
