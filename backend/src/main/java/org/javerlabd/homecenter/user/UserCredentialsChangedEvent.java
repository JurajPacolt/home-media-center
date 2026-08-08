package org.javerlabd.homecenter.user;

/**
 * Používateľovi sa zmenilo heslo alebo PIN, prípadne mu niekto vypol účet. Prihlásené
 * zariadenia treba odhlásiť — inak by starý token prežil práve tú zmenu, ktorá ho mala
 * zneplatniť.
 *
 * <p>Ide to udalosťou, nie priamym volaním: {@code AuthTokenService} potrebuje
 * {@code UserService}, takže opačná závislosť by uzavrela kruh.
 *
 * @param reason čo sa stalo — ide len do logu
 */
public record UserCredentialsChangedEvent(long userId, String reason) {
}
