package org.javerland.homecenter.user;

/**
 * A role determines which areas a user can access. {@link #USER} is intended exclusively
 * for the Android TV client, and Spring Security does not allow it into the management UI.
 */
public enum Role {

    ADMIN("Správca", "Management UI aj TV klient"),
    USER("Používateľ", "Iba TV klient");

    private final String label;
    private final String description;

    Role(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** Spring Security expects authorities with the {@code ROLE_} prefix. */
    public String authority() {
        return "ROLE_" + name();
    }
}
