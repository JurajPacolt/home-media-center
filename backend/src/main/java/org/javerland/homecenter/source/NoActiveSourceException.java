package org.javerland.homecenter.source;

/** No enabled Samba source is configured; scanning and streaming require one. */
public class NoActiveSourceException extends RuntimeException {

    public NoActiveSourceException() {
        super("Nie je nastavený žiadny zapnutý Samba zdroj. Nastav ho v management UI.");
    }

    public NoActiveSourceException(String message) {
        super(message);
    }
}
