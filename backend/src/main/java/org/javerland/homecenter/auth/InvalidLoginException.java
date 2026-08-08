package org.javerland.homecenter.auth;

/**
 * Incorrect username, password, or PIN, or a disabled account. A single message is used
 * intentionally for all cases so the response cannot reveal which usernames exist.
 *
 * <p>It intentionally does not extend {@code AuthenticationException}, which would be
 * caught by {@code ExceptionTranslationFilter} and replaced with an empty, unexplained
 * 401 response.
 */
public class InvalidLoginException extends RuntimeException {

    public InvalidLoginException() {
        super("Nesprávne meno, heslo alebo PIN");
    }
}
