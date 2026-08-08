package org.javerlabd.homecenter.source;

/** Samba je nedostupná alebo odmietla operáciu. */
public class SmbAccessException extends RuntimeException {

    public SmbAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public SmbAccessException(String message) {
        super(message);
    }
}
