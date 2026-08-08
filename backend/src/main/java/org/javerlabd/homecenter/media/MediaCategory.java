package org.javerlabd.homecenter.media;

/**
 * Tri dlaždice, ktoré vidí používateľ na TV. Zámerne sa nevolá MediaType — to je
 * v controlleroch obsadené triedou {@code org.springframework.http.MediaType}.
 */
public enum MediaCategory {

    VIDEO("Videá"),
    PHOTO("Fotky"),
    AUDIO("Hudba");

    private final String label;

    MediaCategory(String label) {
        this.label = label;
    }

    /** Slovenský názov pre management UI. */
    public String label() {
        return label;
    }
}
