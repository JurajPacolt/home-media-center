package org.javerlabd.homecenter.user;

import org.jspecify.annotations.Nullable;

/**
 * Input to {@link UserService#save(UserDraft)} containing form data still in plaintext.
 * As with a Samba source, an empty password during editing means "keep the existing value,"
 * not "delete"; the UI never displays the stored hash.
 *
 * @param id       {@code null} for a new user
 * @param password plaintext password; empty during editing means unchanged
 * @param pin      plaintext PIN; empty means unchanged
 * @param clearPin explicitly removes the PIN and takes precedence over {@code pin}
 */
public record UserDraft(
        @Nullable Long id,
        String username,
        String displayName,
        Role role,
        boolean enabled,
        @Nullable String password,
        @Nullable String pin,
        boolean clearPin) {
}
