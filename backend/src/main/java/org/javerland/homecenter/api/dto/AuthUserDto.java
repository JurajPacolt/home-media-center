package org.javerland.homecenter.api.dto;

import org.javerland.homecenter.user.AppUser;
import org.javerland.homecenter.user.Role;

/** Authenticated user. Password and PIN hashes are intentionally excluded. */
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
