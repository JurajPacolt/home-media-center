package org.javerlabd.homecenter.user;

/** Heslo alebo PIN nespĺňa formát — chytá to formulár aj REST API. */
public class InvalidCredentialFormatException extends RuntimeException {

    public InvalidCredentialFormatException(String message) {
        super(message);
    }
}
