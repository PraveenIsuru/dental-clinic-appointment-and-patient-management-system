package lk.icbt.dentalclinic.service;

import lk.icbt.dentalclinic.dao.UserDao;
import lk.icbt.dentalclinic.model.User;
import lk.icbt.dentalclinic.security.PasswordHasher;
import lk.icbt.dentalclinic.security.Session;
import lk.icbt.dentalclinic.security.SessionManager;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Sign-in, sign-out and lock-out policy. Realises the Login sequence diagram.
 *
 * <p>Depends on the {@link UserDao} <em>interface</em>, never on the JDBC class, so the
 * whole of this logic is unit testable against an in-memory stand-in with no database
 * running. That is the concrete payoff of the DAO pattern, and the tests in
 * {@code AuthServiceTest} are the evidence.
 */
public final class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());

    /** Attempts before an account locks. */
    public static final int MAX_FAILED_ATTEMPTS = 5;
    /** How long the lock lasts. */
    public static final int LOCKOUT_MINUTES = 15;

    /**
     * A real PBKDF2 hash of a value nobody knows, used to burn the same CPU time on an
     * unknown username as on a known one. Without it, "no such user" returns in
     * microseconds while a real user costs ~100ms of key derivation — a difference an
     * attacker can measure over a network to enumerate accounts, defeating the generic
     * error message entirely.
     */
    private final String timingDecoyHash;

    private final UserDao userDao;
    private final PasswordHasher hasher;
    private final SessionManager sessions;

    public AuthService(UserDao userDao, PasswordHasher hasher, SessionManager sessions) {
        this.userDao = userDao;
        this.hasher = hasher;
        this.sessions = sessions;
        this.timingDecoyHash = hasher.hash("a-password-no-account-has");
    }

    /**
     * Attempts a sign-in.
     *
     * @param previousSessionId the session id the browser presented, if any; it is
     *                          invalidated on success so that a fixed identifier planted
     *                          before authentication cannot survive it
     */
    public AuthResult authenticate(String username, char[] password, String previousSessionId) {
        Optional<User> found = username == null || username.isBlank()
                ? Optional.empty()
                : userDao.findByUsername(username.trim());

        if (found.isEmpty()) {
            hasher.verify(password, timingDecoyHash);
            LOG.fine("Sign-in refused: no such user");
            return AuthResult.invalidCredentials();
        }

        User user = found.get();

        if (user.isLocked()) {
            LOG.warning(() -> "Sign-in refused: " + user.getUsername() + " is locked");
            return AuthResult.locked(user.getLockedUntil());
        }

        if (!user.isActive()) {
            hasher.verify(password, timingDecoyHash);
            LOG.warning(() -> "Sign-in refused: " + user.getUsername() + " is disabled");
            return AuthResult.invalidCredentials();
        }

        if (!user.passwordMatches(password, hasher::verify)) {
            return handleFailedAttempt(user);
        }

        return handleSuccess(user, password, previousSessionId);
    }

    private AuthResult handleSuccess(User user, char[] password, String previousSessionId) {
        userDao.recordSuccessfulLogin(user.getUserId());

        // Transparent upgrade: if this hash predates a rise in the iteration count,
        // re-hash it now, while the plaintext is briefly available.
        if (user.needsRehash(hasher::needsRehash)) {
            userDao.updatePasswordHash(user.getUserId(), hasher.hash(password));
            LOG.info(() -> "Upgraded the stored password hash for " + user.getUsername());
        }

        // Session fixation: discard whatever identifier the browser arrived with
        // before minting the authenticated one.
        sessions.invalidate(previousSessionId);
        Session session = sessions.createFor(user);

        LOG.info(() -> "Signed in: " + user.getUsername() + " (" + user.getRole() + ")");
        return AuthResult.success(session);
    }

    private AuthResult handleFailedAttempt(User user) {
        int attempts = userDao.recordFailedAttempt(user.getUserId());

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            LocalDateTime until = LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES);
            userDao.lockUntil(user.getUserId(), until);
            LOG.warning(() -> "Locked " + user.getUsername() + " after " + attempts
                    + " failed attempts");
            return AuthResult.locked(until);
        }

        LOG.fine(() -> "Sign-in refused: wrong password, attempt " + attempts);
        return AuthResult.invalidCredentials();
    }

    /** Ends a session. Idempotent, so a double submit or a stale tab is harmless. */
    public void signOut(String sessionId) {
        sessions.invalidate(sessionId);
    }
}
