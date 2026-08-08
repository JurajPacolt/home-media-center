package org.javerlabd.homecenter.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.javerlabd.homecenter.source.SmbSource;
import org.jspecify.annotations.Nullable;

/**
 * Samba source configuration form. It is not a record because {@code th:field} needs setters.
 * The password is never prefilled; an empty field means "keep the existing value."
 */
@Getter
@Setter
public class SmbSourceForm {

    private @Nullable Long id;

    @NotBlank(message = "Zadaj názov zdroja")
    private String name = "";

    @NotBlank(message = "Zadaj adresu servera")
    private String host = "";

    @Min(value = 1, message = "Port musí byť 1–65535")
    @Max(value = 65535, message = "Port musí byť 1–65535")
    private int port = SmbSource.DEFAULT_PORT;

    @NotBlank(message = "Zadaj názov zdieľaného priečinka")
    private String shareName = "";

    private String rootPath = "";

    private @Nullable String domain;

    private @Nullable String username;

    private @Nullable String password;

    private boolean enabled = true;

    public static SmbSourceForm from(SmbSource source) {
        SmbSourceForm form = new SmbSourceForm();
        form.id = source.id();
        form.name = source.name();
        form.host = source.host();
        form.port = source.port();
        form.shareName = source.shareName();
        form.rootPath = source.rootPath();
        form.domain = source.domain();
        form.username = source.username();
        form.enabled = source.enabled();
        return form;
    }

    public SmbSource toSource() {
        return new SmbSource(id, name, host, port, shareName, rootPath, domain, username,
                password, enabled, null, null);
    }

    /** For a stored source, the database password remains unchanged unless the form changes it. */
    public boolean hasStoredPassword() {
        return id != null;
    }

    public boolean isNew() {
        return id == null;
    }
}
