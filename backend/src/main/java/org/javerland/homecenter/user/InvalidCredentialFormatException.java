package org.javerland.homecenter.user;

/** The password or PIN has an invalid format; both the form and REST API handle this. */
public class InvalidCredentialFormatException extends RuntimeException {

    public InvalidCredentialFormatException(String message) {
        super(message);
    }
}
