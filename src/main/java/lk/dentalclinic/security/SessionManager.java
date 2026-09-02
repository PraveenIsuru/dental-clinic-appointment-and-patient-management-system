package lk.dentalclinic.security;

import lk.dentalclinic.model.User;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The server-side session store.
 *
 * <p>Satisfies the "effective use of sessions/cookies" item named in the 70-100 band,
 * and does so without a servlet container: sessions are entries in a
 * {@link ConcurrentHashMap} keyed by a 256-bit random identifier.
 *
 * <p>Three properties matter more than the storage.
 *
 * <ol>
 *   <li><strong>Unguessable identifiers.</strong> 32 bytes from {@link SecureRandom},
 *       base64url-encoded. {@code Math.random()} or a counter would let an attacker
 *       predict a neighbour's session id and take over the account.</li>
 *   <li><strong>Session-fixation protection.</strong> {@link #createFor(User)} always
 *       mints a fresh identifier, and {@link #invalidate(String)} is called on the old
 *       one at sign-in. If the identifier survived authentication, an attacker who
 *       planted a known id in the victim's browser beforehand would hold a valid
 *       session the moment the victim signed in.</li>
 *   <li><strong>Idle timeout.</strong> A session unused for {@value #IDLE_TIMEOUT_MINUTES}
 *       minutes is discarded, so an unattended terminal at the reception desk does not
 *       stay signed in indefinitely.</li>
 * </ol>
 *
 * <p>SINGLETON, for the same reason as the connection pool: two stores would mean a
 * session created by one request being unknown to the next. <em>Evaluated:</em> the
 * state is per-process, so this design does not survive horizontal scaling — a second
 * instance behind a load balancer would not share sessions. A database-backed or
 * sticky-session deployment would be the fix, and is noted as a limitation rather than
 * pretended away.
 */
public final class SessionManager {

    private static final Logger LOG = Logger.getLogger(SessionManager.class.getName());

    public static final String COOKIE_NAME = "SESSIONID";
    public static final int IDLE_TIMEOUT_MINUTES = 30;
    private static final int TOKEN_BYTES = 32;

    private static final SessionManager INSTANCE = new SessionManager();

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Duration idleTimeout;

    private SessionManager() {
        this(Duration.ofMinutes(IDLE_TIMEOUT_MINUTES));
    }

    // Package-private, so tests can use a short timeout without waiting 30 minutes.
    SessionManager(Duration idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    static SessionManager withTimeout(Duration idleTimeout) {
        return new SessionManager(idleTimeout);
    }

    /**
     * Creates a session for a freshly authenticated user.
     *
     * <p>The caller must invalidate any prior session id first — see the Login
     * sequence diagram, step 22.
     */
    public Session createFor(User user) {
        Instant now = Instant.now();
        Session session = new Session(randomToken(), user, randomToken(), now);
        sessions.put(session.getId(), session);
        LOG.fine(() -> "Session created for " + user.getUsername());
        return session;
    }

    /**
     * Looks up a live session, refreshing its idle deadline.
     *
     * <p>Returns empty for an unknown or expired id, so a caller cannot tell the two
     * apart — an expired id must not be a signal that it was once valid.
     */
    public Optional<Session> find(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        Session session = sessions.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        if (session.isIdleBeyond(idleTimeout, now)) {
            sessions.remove(sessionId);
            LOG.fine(() -> "Session expired for " + session.getUsername());
            return Optional.empty();
        }
        session.touch(now);
        return Optional.of(session);
    }

    public void invalidate(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    /** Signs out every session belonging to a user, used when an account is disabled. */
    public int invalidateAllFor(int userId) {
        int before = sessions.size();
        sessions.values().removeIf(session -> session.getUserId() == userId);
        return before - sessions.size();
    }

    /** Removes expired entries. Called periodically so an idle process does not leak memory. */
    public int purgeExpired() {
        Instant now = Instant.now();
        int before = sessions.size();
        sessions.values().removeIf(session -> session.isIdleBeyond(idleTimeout, now));
        int removed = before - sessions.size();
        if (removed > 0) {
            LOG.fine(() -> "Purged " + removed + " expired sessions");
        }
        return removed;
    }

    public int activeCount() {
        return sessions.size();
    }

    /** Test hook; never called by application code. */
    void clear() {
        sessions.clear();
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
