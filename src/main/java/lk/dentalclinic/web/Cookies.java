package lk.icbt.dentalclinic.web;

import com.sun.net.httpserver.HttpExchange;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and writes HTTP cookies.
 *
 * <p>Every cookie this application sets carries the same three protections:
 *
 * <ul>
 *   <li>{@code HttpOnly} — JavaScript cannot read it, so an injected script cannot
 *       exfiltrate the session identifier.</li>
 *   <li>{@code SameSite=Strict} — the browser will not attach it to a request
 *       originating from another site, which blocks cross-site request forgery at
 *       the transport level. The CSRF token is defence in depth behind it.</li>
 *   <li>{@code Path=/} — one cookie for the whole application rather than a set the
 *       browser sends inconsistently.</li>
 * </ul>
 *
 * <p>{@code Secure} is deliberately conditional: forcing it would break the local
 * HTTP development server. It is switched on by configuration for the M6 deployment,
 * which is served over TLS.
 */
public final class Cookies {

    private Cookies() {
    }

    /** Parses the request's {@code Cookie} header. */
    public static Map<String, String> read(HttpExchange exchange) {
        Map<String, String> cookies = new LinkedHashMap<>();
        List<String> headers = exchange.getRequestHeaders().get("Cookie");
        if (headers == null) {
            return cookies;
        }
        for (String header : headers) {
            for (String pair : header.split(";")) {
                int equals = pair.indexOf('=');
                if (equals > 0) {
                    cookies.put(pair.substring(0, equals).trim(),
                            pair.substring(equals + 1).trim());
                }
            }
        }
        return cookies;
    }

    public static Optional<String> get(HttpExchange exchange, String name) {
        return Optional.ofNullable(read(exchange).get(name));
    }

    /** A session cookie: no Max-Age, so it dies when the browser closes. */
    public static void set(HttpExchange exchange, String name, String value, boolean secure) {
        add(exchange, build(name, value, null, secure));
    }

    /** A persistent cookie, used for the 14-day "remember me" option. */
    public static void set(HttpExchange exchange, String name, String value,
                           Duration maxAge, boolean secure) {
        add(exchange, build(name, value, maxAge, secure));
    }

    /**
     * Expires a cookie.
     *
     * <p>{@code Max-Age=0} with an empty value: a browser only removes a cookie when
     * the attributes match, so this must mirror the {@code Path} used when setting it.
     */
    public static void clear(HttpExchange exchange, String name) {
        add(exchange, name + "=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict");
    }

    private static String build(String name, String value, Duration maxAge, boolean secure) {
        StringBuilder cookie = new StringBuilder(name).append('=').append(value)
                .append("; Path=/; HttpOnly; SameSite=Strict");
        if (maxAge != null) {
            cookie.append("; Max-Age=").append(maxAge.toSeconds());
        }
        if (secure) {
            cookie.append("; Secure");
        }
        return cookie.toString();
    }

    private static void add(HttpExchange exchange, String cookie) {
        // add(), not set(): a response may carry several Set-Cookie headers, and
        // replacing them would drop the session cookie when the remember-me cookie
        // is written in the same response.
        exchange.getResponseHeaders().add("Set-Cookie", cookie);
    }
}
