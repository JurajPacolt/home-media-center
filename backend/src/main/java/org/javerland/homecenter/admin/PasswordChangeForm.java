package org.javerland.homecenter.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Changes the current password. The old password prevents an unattended session from replacing it. */
@Getter
@Setter
public class PasswordChangeForm {

    @NotBlank(message = "Zadaj súčasné heslo")
    private String currentPassword = "";

    @NotBlank(message = "Zadaj nové heslo")
    private String newPassword = "";

    @NotBlank(message = "Zopakuj nové heslo")
    private String confirmPassword = "";

    public boolean confirmationMatches() {
        return newPassword.equals(confirmPassword);
    }
}
