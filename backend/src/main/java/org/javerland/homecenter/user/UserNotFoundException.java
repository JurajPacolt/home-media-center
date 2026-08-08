package org.javerland.homecenter.user;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(long id) {
        super("Používateľ s id " + id + " neexistuje");
    }
}
