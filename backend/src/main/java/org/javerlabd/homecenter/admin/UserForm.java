package org.javerlabd.homecenter.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.javerlabd.homecenter.user.AppUser;
import org.javerlabd.homecenter.user.Role;
import org.javerlabd.homecenter.user.UserDraft;
import org.jspecify.annotations.Nullable;

/**
 * Formulár používateľa. Nie je to record — {@code th:field} potrebuje settery.
 * Heslo ani PIN sa nikdy nepredvypĺňajú; prázdne pole znamená „nechať pôvodné“.
 */
@Getter
@Setter
public class UserForm {

    private @Nullable Long id;

    @NotBlank(message = "Zadaj používateľské meno")
    @Size(max = 64, message = "Meno je pridlhé")
    private String username = "";

    @Size(max = 255, message = "Zobrazované meno je pridlhé")
    private String displayName = "";

    private Role role = Role.USER;

    private boolean enabled = true;

    private @Nullable String password;

    private @Nullable String pin;

    /** Zaškrtnutím sa PIN zruší — používateľ sa potom prihlási už len heslom. */
    private boolean clearPin;

    /** Či má už uložený PIN; podľa toho sa v šablóne mení nápoveda. */
    private boolean pinSet;

    public static UserForm from(AppUser user) {
        UserForm form = new UserForm();
        form.id = user.id();
        form.username = user.username();
        form.displayName = user.displayName();
        form.role = user.role();
        form.enabled = user.enabled();
        form.pinSet = user.hasPin();
        return form;
    }

    public UserDraft toDraft() {
        return new UserDraft(id, username, displayName, role, enabled, password, pin, clearPin);
    }

    public boolean isNew() {
        return id == null;
    }
}
