package lk.dentalclinic.web;

import lk.dentalclinic.security.Session;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Per-request state.
 *
 * <p><strong>Why not {@code HttpExchange.setAttribute()}.</strong> The obvious home for
 * this is the exchange's own attribute map, and that is what this class used first. It
 * is a trap. In the JDK's implementation {@code ExchangeImpl.setAttribute} delegates to
 * {@code HttpContextImpl.getAttributes()} — a map owned by the <em>context</em>, which is
 * created once per {@code createContext("/", ...)} call and shared by every request the
 * server ever handles. Storing the signed-in session there published it to all of them:
 * once any user signed in, the next request with no cookie at all was served that user's
 * session, and the admin dashboard rendered for an anonymous visitor. That is a complete
 * authentication bypass, and the name {@code setAttribute} on a per-request object gives
 * no hint of it.
 *
 * <p>It was caught by {@code LoginFlowIT}, which issues several requests in sequence
 * against one server. A test making a single request per server cannot see it, which is
 * the argument for integration tests that reuse a server the way a real client does.
 *
 * <p>The replacement is a {@link ThreadLocal} map. The JDK's {@code HttpServer} dispatches
 * each exchange to one worker thread and the whole chain runs on it, so thread-scoped and
 * request-scoped coincide — provided the map is cleared when the request ends, which
 * {@link Router} does in a {@code finally} block. Without that clearing the same leak
 * returns by another route, since worker threads are pooled and reused.
 */
public final class WebContext {

    private static final String SESSION = "session";

    private static final ThreadLocal<Map<String, Object>> STATE =
            ThreadLocal.withInitial(HashMap::new);

    private WebContext() {
    }

    // ------------------------------------------------------------- lifecycle

    /**
     * Discards everything held for the current request.
     *
     * <p>Called by {@link Router} in a {@code finally}. {@code remove()} rather than
     * {@code get().clear()}: the worker thread may sit idle for a long time afterwards,
     * and there is no reason to keep the map — or anything it references — alive.
     */
    static void clear() {
        STATE.remove();
    }

    // ------------------------------------------------------- generic attributes

    static void put(String key, Object value) {
        STATE.get().put(key, value);
    }

    static Object get(String key) {
        return STATE.get().get(key);
    }

    // ---------------------------------------------------------------- session

    public static void setSession(Session session) {
        put(SESSION, session);
    }

    public static Optional<Session> session() {
        return get(SESSION) instanceof Session session ? Optional.of(session) : Optional.empty();
    }

    /**
     * The signed-in session, or a failure if there is none.
     *
     * <p>Only for handlers behind an authenticated rule: reaching this without a session
     * means the access rules and the routing table disagree, which is a programming error
     * rather than a user error.
     */
    public static Session requireSession() {
        return session().orElseThrow(() -> new IllegalStateException(
                "No session on a protected route; access rules and routes disagree"));
    }

    public static boolean isSignedIn() {
        return session().isPresent();
    }
}
