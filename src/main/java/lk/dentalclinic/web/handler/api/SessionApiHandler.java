package lk.dentalclinic.web.handler.api;

import lk.dentalclinic.security.Session;
import lk.dentalclinic.web.Handler;
import lk.dentalclinic.web.WebContext;
import lk.dentalclinic.web.json.Json;

/**
 * {@code GET /api/v1/session} — who the caller is, and the CSRF token they need.
 *
 * <p>The first call any API client makes after signing in. Without it a client
 * authenticated by cookie has no way to obtain the token that
 * {@link lk.dentalclinic.web.filter.CsrfFilter} requires on every write — the token
 * is in the HTML pages, which an API client does not fetch. Publishing it here is the
 * same decision as the {@code <meta>} tag on the web pages, and safe for the same reason:
 * the token defends against a cross-site forgery, and an attacker on another origin
 * cannot read this response.
 *
 * <p>Also serves as the API's "am I still signed in?" probe, which saves a client from
 * discovering an expired session by having a write fail.
 */
public final class SessionApiHandler {

    private SessionApiHandler() {
    }

    public static Handler current() {
        return ApiSupport.guard(exchange -> {
            Session session = WebContext.requireSession();

            ApiSupport.ok(exchange, Json.object()
                    .put("username", session.getUsername())
                    .put("fullName", session.getFullName())
                    .put("role", session.getRole())
                    .put("csrfToken", session.getCsrfToken())
                    .put("csrfHeader", lk.dentalclinic.web.filter.CsrfFilter.HEADER));
        });
    }
}
