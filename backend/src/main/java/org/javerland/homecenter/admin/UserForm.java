package org.javerland.homecenter.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.javerland.homecenter.user.AppUser;
import org.javerland.homecenter.user.Role;
import org.javerland.homecenter.user.UserDraft;
import org.jspecify.annotations.Nullable;

/**
 * User form. It is not a record because {@code th:field} needs setters.
 * The password and PIN are never prefilled; an empty field means "keep the existing value."
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

    /** Selecting this removes the PIN, after which the user can log in only with a password. */
    private boolean clearPin;

    /** Whether a PIN is already stored; the template changes its hint accordingly. */
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
