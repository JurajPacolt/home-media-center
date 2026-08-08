package org.javerlabd.homecenter.auth;

import java.util.Collection;
import java.util.List;

import org.javerlabd.homecenter.user.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Most medzi doménovým {@link AppUser} a Spring Security. Nesie so sebou celého
 * používateľa, takže controllery sa k nemu dostanú cez {@code @AuthenticationPrincipal}
 * bez ďalšieho dotazu do databázy.
 */
public record AuthenticatedUser(AppUser user) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(user.role().authority()));
    }

    /** Argon2 hash — porovnáva ho {@code DaoAuthenticationProvider} pri prihlásení do UI. */
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
