package org.javerlabd.homecenter.source;

/** Samba is unavailable or rejected an operation. */
public class SmbAccessException extends RuntimeException {

    public SmbAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public SmbAccessException(String message) {
        super(message);
    }
}
