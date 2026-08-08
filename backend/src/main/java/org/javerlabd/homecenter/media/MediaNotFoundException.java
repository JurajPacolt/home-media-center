package org.javerlabd.homecenter.media;

/** V indexe nie je položka s daným id. */
public class MediaNotFoundException extends RuntimeException {

    public MediaNotFoundException(long id) {
        super("Médium s id " + id + " nie je v indexe");
    }
}
