package org.javerlabd.homecenter.user;

import org.jspecify.annotations.Nullable;

/**
 * Vstup do {@link UserService#save(UserDraft)} — to, čo prišlo z formulára, ešte
 * v otvorenom tvare. Rovnako ako pri Samba zdroji platí, že prázdne heslo pri úprave
 * znamená „nechať pôvodné“, nie „zmazať“; UI uložený hash nikdy nezobrazuje.
 *
 * @param id       {@code null} pre nového používateľa
 * @param password otvorené heslo; pri úprave prázdne = nemeniť
 * @param pin      otvorený PIN; prázdny = nemeniť
 * @param clearPin explicitné zrušenie PINu (má prednosť pred {@code pin})
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
