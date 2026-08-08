package org.javerlabd.homecenter.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Zmena vlastného hesla. Staré heslo sa pýta preto, aby odídená relácia nestačila na jeho prepísanie. */
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
