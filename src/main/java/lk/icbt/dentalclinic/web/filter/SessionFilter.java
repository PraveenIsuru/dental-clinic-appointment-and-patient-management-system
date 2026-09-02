package lk.icbt.dentalclinic.web.filter;

import com.sun.net.httpserver.HttpExchange;
import lk.icbt.dentalclinic.security.SessionManager;
import lk.icbt.dentalclinic.web.Cookies;
import lk.icbt.dentalclinic.web.Filter;
import lk.icbt.dentalclinic.web.Handler;
import lk.icbt.dentalclinic.web.WebContext;

import java.io.IOException;

/**
 * Resolves the {@code SESSIONID} cookie into a {@link lk.icbt.dentalclinic.security.Session}
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
