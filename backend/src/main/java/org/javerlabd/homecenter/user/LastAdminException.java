package org.javerlabd.homecenter.user;

/**
 * Poistka proti zamknutiu sa zo servera: posledný zapnutý správca sa nedá zmazať,
 * vypnúť ani preradiť na {@link Role#USER}. Bez nej by sa do management UI
 * nedalo dostať inak než zásahom do databázy.
 */
public class LastAdminException extends RuntimeException {

    public LastAdminException() {
        super("Toto je posledný zapnutý správca — inak by sa do management UI už nikto nedostal");
    }
}
