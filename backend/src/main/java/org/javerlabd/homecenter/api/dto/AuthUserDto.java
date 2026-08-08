package org.javerlabd.homecenter.api.dto;

import org.javerlabd.homecenter.user.AppUser;
import org.javerlabd.homecenter.user.Role;

/** Prihlásený používateľ. Hash hesla ani PINu sa sem zámerne nedostane. */
public record AuthUserDto(
        long id,
        String username,
        String displayName,
        Role role,
        boolean hasPin) {

    public static AuthUserDto from(AppUser user) {
        return new AuthUserDto(
                user.requireId(),
                user.username(),
                user.displayName(),
                user.role(),
                user.hasPin());
    }
}
