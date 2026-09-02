package lk.icbt.dentalclinic.web.filter;

import com.sun.net.httpserver.HttpExchange;
import lk.icbt.dentalclinic.security.AccessRules;
import lk.icbt.dentalclinic.security.Session;
import lk.icbt.dentalclinic.web.Filter;
import lk.icbt.dentalclinic.web.Handler;
import lk.icbt.dentalclinic.web.Pages;
import lk.icbt.dentalclinic.web.Responses;
import lk.icbt.dentalclinic.web.WebContext;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Enforces the URL rules in {@link AccessRules}.
 *
 * <p>The two refusals are deliberately different:
 *
 * <ul>
 *   <li><strong>Not signed in</strong> — redirect to {@code /login}, carrying the
 *       requested path so the user lands where they meant to go. The path is
 *       URL-encoded and validated on the way back out, because an open redirect here
 *       would let an attacker send {@code /login?next=https://evil.example} in a
 *       phishing mail and have the clinic's own domain bounce the victim onward.</li>
 *   <li><strong>Signed in, wrong role</strong> — 403 with an explanatory page. This is
 *       a role-level refusal, and 403 is correct for it. Record-level refusals are
 *       different: a patient reaching another patient's appointment gets 404, so the
 *       response cannot confirm that the record exists (A6).</li>
 * </ul>
 */
public final class AuthorizationFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(AuthorizationFilter.class.getName());

    private final AccessRules rules;

    public AuthorizationFilter(AccessRules rules) {
        this.rules = rules;
    }

    @Override
    public void handle(HttpExchange exchange, Handler next) throws IOException {
        String path = exchange.getRequestURI().getPath();
        Session session = WebContext.session().orElse(null);

        if (rules.isAllowed(path, session)) {
            next.handle(exchange);
            return;
        }

        // An API client is sent JSON, not a redirect to a login page it cannot render.
        // 401 rather than 303 tells it to authenticate; 403 tells it not to bother.
        if (isApiRequest(path)) {
            int status = session == null ? 401 : 403;
            String code = session == null ? "unauthenticated" : "forbidden";
            String message = session == null
                    ? "Sign in first; this API uses the session cookie."
                    : "Your role does not have access to this resource.";
            Responses.json(exchange, status,
                    "{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
            return;
        }

        if (session == null) {
            String target = URLEncoder.encode(path, StandardCharsets.UTF_8);
            Responses.redirect(exchange, "/login?next=" + target);
            return;
        }

        LOG.warning(() -> "Refused " + session.getUsername() + " (" + session.getRole()
                + ") access to " + path);
        Responses.html(exchange, 403, Pages.forbidden(session.dashboardPath()));
    }

    /** Whether the request is for the REST API rather than a browser page. */
    static boolean isApiRequest(String path) {
        return path.startsWith("/api/");
    }
}
