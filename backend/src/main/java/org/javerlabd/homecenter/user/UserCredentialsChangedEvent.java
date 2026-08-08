package org.javerlabd.homecenter.user;

/**
 * A user's password or PIN changed, or the account was disabled. Authenticated devices
 * must be logged out; otherwise, an old token would survive the very change intended to
 * invalidate it.
 *
 * <p>This uses an event rather than a direct call: {@code AuthTokenService} depends on
 * {@code UserService}, so the reverse dependency would create a cycle.
 *
 * @param reason what happened; used only in the log
 */
public record UserCredentialsChangedEvent(long userId, String reason) {
}
