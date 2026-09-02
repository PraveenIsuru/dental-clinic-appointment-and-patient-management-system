package lk.icbt.dentalclinic.web.handler;

import com.sun.net.httpserver.HttpExchange;
import lk.icbt.dentalclinic.security.SessionManager;
import lk.icbt.dentalclinic.service.AuthService;
import lk.icbt.dentalclinic.web.Cookies;
import lk.icbt.dentalclinic.web.Handler;
import lk.icbt.dentalclinic.web.Responses;

import java.io.IOException;

/**
 * Ends the session — scenario requirement 6, "allow users to safely close the
 * application".
 *
 * <p>Both halves are needed. Clearing the cookie alone would leave the session alive on
 * the server, so anyone holding a copy of the identifier could still use it; destroying
 * the server-side session alone would leave the browser resending a dead cookie.
 *
 * <p>Accepts GET as well as POST. A sign-out link is the plainer interface for a
 * receptionist leaving a shared terminal, and the worst a forged sign-out can do is
 * inconvenience someone.
 */
public final class LogoutHandler implements Handler {

    private final AuthService authService;

    public LogoutHandler(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Cookies.get(exchange, SessionManager.COOKIE_NAME).ifPresent(authService::signOut);
        Cookies.clear(exchange, SessionManager.COOKIE_NAME);
        Responses.redirect(exchange, "/login?signedOut=1");
    }
}
