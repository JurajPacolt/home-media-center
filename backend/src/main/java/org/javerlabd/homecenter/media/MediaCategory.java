package org.javerlabd.homecenter.media;

/**
 * The three tiles visible to the TV user. It is intentionally not named MediaType
 * because controllers already use {@code org.springframework.http.MediaType}.
 */
public enum MediaCategory {

    VIDEO("Videá"),
    PHOTO("Fotky"),
    AUDIO("Hudba");

    private final String label;

    MediaCategory(String label) {
        this.label = label;
    }

    /** Slovak display name for the management UI. */
    public String label() {
        return label;
    }
}
