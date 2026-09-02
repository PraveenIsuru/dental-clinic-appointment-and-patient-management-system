package lk.dentalclinic.service;

import lk.dentalclinic.security.Session;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * The outcome of a sign-in attempt.
 *
 * <p>{@link Outcome#INVALID_CREDENTIALS} covers an unknown username, a wrong password
 * and a disabled account alike. The caller therefore cannot render a different message
 * for each, which is the point: "no such user" and "wrong password" together let an
 * attacker enumerate valid accounts before attacking any of them.
 *
 * <p>{@link Outcome#LOCKED} is the deliberate exception. Telling a locked-out user when
 * they may try again is worth more than the small amount it reveals, since reaching the
 * locked state already required five failed attempts against a real account.
 */
public record AuthResult(Outcome outcome, Session session, LocalDateTime lockedUntil) {

    public enum Outcome {
        SUCCESS,
        INVALID_CREDENTIALS,
        LOCKED
    }

    public static AuthResult success(Session session) {
        return new AuthResult(Outcome.SUCCESS, session, null);
    }

    public static AuthResult invalidCredentials() {
        return new AuthResult(Outcome.INVALID_CREDENTIALS, null, null);
    }

    public static AuthResult locked(LocalDateTime until) {
        return new AuthResult(Outcome.LOCKED, null, until);
    }

    public boolean isSuccess() {
        return outcome == Outcome.SUCCESS;
    }

    public Optional<Session> sessionIfSuccessful() {
        return Optional.ofNullable(session);
    }

    /** The message shown to the user. Identical for every failure but a lock-out. */
    public String userMessage() {
        return switch (outcome) {
            case SUCCESS -> "";
            case INVALID_CREDENTIALS -> "Sign-in details are incorrect.";
            case LOCKED -> "This account is temporarily locked after repeated failed attempts. "
                    + "Try again later, or ask an administrator to unlock it.";
        };
    }
}
