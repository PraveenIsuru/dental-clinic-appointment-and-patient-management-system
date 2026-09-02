package lk.icbt.dentalclinic.web.handler;

import com.sun.net.httpserver.HttpExchange;
import lk.icbt.dentalclinic.config.AppConfig;
import lk.icbt.dentalclinic.security.Session;
import lk.icbt.dentalclinic.security.SessionManager;
import lk.icbt.dentalclinic.service.AuthResult;
import lk.icbt.dentalclinic.service.AuthService;
import lk.icbt.dentalclinic.web.Cookies;
import lk.icbt.dentalclinic.web.Handler;
import lk.icbt.dentalclinic.web.Requests;
import lk.icbt.dentalclinic.web.Responses;
import lk.icbt.dentalclinic.web.View;
import lk.icbt.dentalclinic.web.WebContext;

import java.io.IOException;
import java.util.Map;

/**
 * {@code GET /login} shows the form; {@code POST /login} attempts the sign-in.
 *
 * <p>Realises the Login sequence diagram, including the cookie attributes and the
 * redirect that follows a successful authentication.
 */
public final class LoginHandler implements Handler {

    private final AuthService authService;
    private final View view;
    private final boolean secureCookies;

    public LoginHandler(AuthService authService, View view, AppConfig config) {
        this.authService = authService;
        this.view = view;
        this.secureCookies = config.cookiesSecure();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            attemptSignIn(exchange);
        } else {
            showForm(exchange);
        }
    }

    private void showForm(HttpExchange exchange) throws IOException {
        // Already signed in? Send them on rather than showing a form they do not need.
        if (WebContext.isSignedIn()) {
            Responses.redirect(exchange, WebContext.requireSession().dashboardPath());
            return;
        }
        Map<String, Object> model = view.model(exchange);
        model.put("next", safeNext(Requests.queryParam(exchange, "next").orElse(null)));
        // Confirmations carried across the redirect that follows registration and
        // sign-out, so neither page has to be re-rendered on a POST.
        Requests.queryParam(exchange, "registered")
                .ifPresent(patientNo -> model.put("registered", patientNo));
        Requests.queryParam(exchange, "signedOut")
                .ifPresent(flag -> model.put("signedOut", true));
        view.render(exchange, "login", model);
    }

    private void attemptSignIn(HttpExchange exchange) throws IOException {
        Map<String, String> form = Requests.form(exchange);
        String username = Requests.field(form, "username");
        char[] password = Requests.field(form, "password").toCharArray();
        String next = safeNext(form.get("next"));

        String presentedSessionId =
                Cookies.get(exchange, SessionManager.COOKIE_NAME).orElse(null);

        AuthResult result = authService.authenticate(username, password, presentedSessionId);

        if (!result.isSuccess()) {
            Map<String, Object> model = view.model(exchange);
            // The username is echoed back so the user need not retype it. The password
            // never is: it would land in the HTML, and from there into browser cache
            // and any proxy log on the way.
            model.put("username", username);
            model.put("next", next);
            model.put("error", result.userMessage());
            view.render(exchange, 401, "login", model);
            return;
        }

        Session session = result.sessionIfSuccessful().orElseThrow();
        Cookies.set(exchange, SessionManager.COOKIE_NAME, session.getId(), secureCookies);
        Responses.redirect(exchange, next != null ? next : session.dashboardPath());
    }

    /**
     * Accepts only a same-site absolute path.
     *
     * <p>Without this, {@code /login?next=https://evil.example} would make the clinic's
     * own domain redirect a signed-in user to an attacker's copy of the login page — an
     * open redirect, and a convincing one precisely because the first link really was
     * the clinic's. A leading {@code //} is rejected too: browsers read it as
     * protocol-relative and treat it as another host.
     */
    private static String safeNext(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        if (!candidate.startsWith("/") || candidate.startsWith("//")) {
            return null;
        }
        return candidate;
    }
}
