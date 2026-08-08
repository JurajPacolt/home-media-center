package org.javerland.homecenter.user;

/**
 * Safeguard against server lockout: the last enabled administrator cannot be deleted,
 * disabled, or changed to {@link Role#USER}. Without it, the management UI could be
 * recovered only by modifying the database.
 */
public class LastAdminException extends RuntimeException {

    public LastAdminException() {
        super("Toto je posledný zapnutý správca — inak by sa do management UI už nikto nedostal");
    }
}
