package org.javerlabd.homecenter.auth;

import java.util.Collection;
import java.util.List;

import org.javerlabd.homecenter.user.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Bridge between the domain {@link AppUser} and Spring Security. It carries the complete
 * user, allowing controllers to access it through {@code @AuthenticationPrincipal}
 * without another database query.
 */
public record AuthenticatedUser(AppUser user) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(user.role().authority()));
    }

    /** Argon2 hash compared by {@code DaoAuthenticationProvider} during UI login. */
    @Override
    public String getPassword() {
        return user.passwordHash();
    }

    @Override
    public String getUsername() {
        return user.username();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.enabled();
    }
}
