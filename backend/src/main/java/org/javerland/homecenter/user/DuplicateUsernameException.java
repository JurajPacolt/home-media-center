package org.javerland.homecenter.user;

public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException(String username) {
        super("Používateľské meno '" + username + "' je už obsadené");
    }
}
