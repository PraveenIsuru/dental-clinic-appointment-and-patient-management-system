package lk.dentalclinic.web.filter;

import com.sun.net.httpserver.HttpExchange;
import lk.dentalclinic.security.Session;
import lk.dentalclinic.web.Filter;
import lk.dentalclinic.web.Handler;
import lk.dentalclinic.web.Pages;
import lk.dentalclinic.web.Requests;
import lk.dentalclinic.web.Responses;
import lk.dentalclinic.web.WebContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Cross-site request forgery protection for state-changing requests.
 *
 * <p>Every form carries a hidden {@code _csrf} field holding a token minted with the
 * session and never sent in a cookie. A forged form on another site can make the
 * victim's browser POST here — the session cookie rides along automatically — but it
 * cannot read the token out of a page it is not allowed to see, so the check fails.
 *
 * <p>This is defence in depth behind {@code SameSite=Strict} on the session cookie,
 * which already stops the browser attaching it to a cross-site POST. Two independent
 * mechanisms means a browser that mishandles {@code SameSite} is not immediately a
 * vulnerability.
 *
 * <p>Only unsafe methods are checked. GET and HEAD must not change state, so requiring
 * a token on them would add nothing and would break ordinary links.
 */
public final class CsrfFilter implements Filter {

    public static final String FIELD = "_csrf";

    /** The header an API client uses instead of a hidden form field. */
    public static final String HEADER = "X-CSRF-Token";

    private static final Logger LOG = Logger.getLogger(CsrfFilter.class.getName());

    @Override
    public void handle(HttpExchange exchange, Handler next) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        boolean unsafe = method.equals("POST") || method.equals("PUT") || method.equals("DELETE");

        if (!unsafe) {
            next.handle(exchange);
            return;
        }

        Session session = WebContext.session().orElse(null);
        if (session == null) {
            // Sign-in and self-registration are posted by users who have no session
            // yet. SameSite=Strict is the protection there; there is no token to check.
            next.handle(exchange);
            return;
        }

        // An API client sends JSON and has no form field to put the token in, so the
        // header is accepted too. Reading the header first also avoids consuming the
        // request body on an API call, which the handler still needs to parse.
        String submitted = exchange.getRequestHeaders().getFirst(HEADER);
        if (submitted == null) {
            Map<String, String> form = Requests.form(exchange);
            submitted = form.get(FIELD);
        }

        if (!matches(session.getCsrfToken(), submitted)) {
            String path = exchange.getRequestURI().getPath();
            LOG.warning(() -> "CSRF token rejected for " + session.getUsername() + " on " + path);

            if (AuthorizationFilter.isApiRequest(path)) {
                Responses.json(exchange, 403, "{\"error\":\"csrf_failed\",\"message\":"
                        + "\"Send the token from GET /api/v1/session in the "
                        + HEADER + " header.\"}");
            } else {
                Responses.html(exchange, 403, Pages.csrfRejected(session.dashboardPath()));
            }
            return;
        }

        next.handle(exchange);
    }

    /**
     * Constant-time comparison. {@link String#equals} returns as soon as two bytes
     * differ, so its timing reveals how much of the token was correct — enough, over
     * many attempts, to reconstruct it.
     */
    private static boolean matches(String expected, String submitted) {
        if (expected == null || submitted == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                submitted.getBytes(StandardCharsets.UTF_8));
    }
}
