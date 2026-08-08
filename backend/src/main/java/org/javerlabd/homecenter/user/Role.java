package org.javerlabd.homecenter.user;

/**
 * Rola rozhoduje o tom, kam sa používateľ dostane. {@link #USER} je určený výhradne
 * pre Android TV klienta — do management UI ho Spring Security nepustí.
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

    /** Spring Security očakáva autority s prefixom {@code ROLE_}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
