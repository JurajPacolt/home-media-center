package org.javerlabd.homecenter.source;

/** Nie je nastavený žiadny zapnutý Samba zdroj — bez neho sa nedá skenovať ani streamovať. */
public class NoActiveSourceException extends RuntimeException {

    public NoActiveSourceException() {
        super("Nie je nastavený žiadny zapnutý Samba zdroj. Nastav ho v management UI.");
    }

    public NoActiveSourceException(String message) {
        super(message);
    }
}
