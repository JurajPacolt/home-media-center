package org.javerlabd.homecenter.auth;

/**
 * Nesprávne meno, heslo alebo PIN — prípadne vypnutý účet. Správa je zámerne jedna
 * pre všetky prípady, aby sa z odpovede nedalo zistiť, ktoré mená v systéme existujú.
 *
 * <p>Nededí od {@code AuthenticationException} úmyselne: tú by odchytil
 * {@code ExceptionTranslationFilter} a nahradil prázdnou 401 bez vysvetlenia.
 */
public class InvalidLoginException extends RuntimeException {

    public InvalidLoginException() {
        super("Nesprávne meno, heslo alebo PIN");
    }
}
