package lk.dentalclinic.web.filter;

import com.sun.net.httpserver.HttpExchange;
import lk.dentalclinic.security.SessionManager;
import lk.dentalclinic.web.Cookies;
import lk.dentalclinic.web.Filter;
import lk.dentalclinic.web.Handler;
import lk.dentalclinic.web.WebContext;

import java.io.IOException;

/**
 * Resolves the {@code SESSIONID} cookie into a {@link lk.dentalclinic.security.Session}
 * and publishes it on the exchange.
 *
 * <p>This filter only <em>identifies</em>; it never rejects. Authorisation is
 * {@link AuthorizationFilter}'s job. Splitting them means a public page can still know
 * who is looking at it — the help page shows staff topics to staff and patient topics
 * to patients — without that page having to opt into authentication.
 *
 * <p>A cookie naming a session that has expired or been signed out is cleared from the
 * browser, so a stale identifier is not resent on every subsequent request.
 */
public final class SessionFilter implements Filter {

    private final SessionManager sessions;

    public SessionFilter(SessionManager sessions) {
        this.sessions = sessions;
    }

    @Override
    public void handle(HttpExchange exchange, Handler next) throws IOException {
        Cookies.get(exchange, SessionManager.COOKIE_NAME).ifPresent(sessionId ->
                sessions.find(sessionId).ifPresentOrElse(
                        session -> WebContext.setSession(session),
                        () -> Cookies.clear(exchange, SessionManager.COOKIE_NAME)));

        next.handle(exchange);
    }
}
