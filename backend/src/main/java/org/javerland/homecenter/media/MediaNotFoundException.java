package org.javerland.homecenter.media;

/** The index contains no item with the given ID. */
public class MediaNotFoundException extends RuntimeException {

    public MediaNotFoundException(long id) {
        super("Médium s id " + id + " nie je v indexe");
    }
}
