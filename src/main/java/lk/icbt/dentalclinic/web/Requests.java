package lk.icbt.dentalclinic.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Reads query strings and {@code application/x-www-form-urlencoded} request bodies. */
public final class Requests {

    /**
     * Caps the body a form post may carry. Without a limit, a client could stream
     * gigabytes into {@code readAllBytes} and exhaust the heap — a denial of service
     * that costs the attacker nothing.
     */
    private static final int MAX_FORM_BYTES = 64 * 1024;

    private static final String FORM_PARAMS = "lk.icbt.dentalclinic.formParams";

    private Requests() {
    }

    /** Parses the query string, if any. */
    public static Map<String, String> query(HttpExchange exchange) {
        return parseUrlEncoded(exchange.getRequestURI().getRawQuery());
    }

    public static Optional<String> queryParam(HttpExchange exchange, String name) {
        return Optional.ofNullable(query(exchange).get(name));
    }

    /**
     * Reads and parses a form body.
     *
     * <p>Cached for the duration of the request: the body is a one-shot stream, so a
     * second read would return nothing, and a handler consulting the form after
     * {@code CsrfFilter} already read it would silently see empty values.
     *
     * <p>The cache lives in {@link WebContext}, not in {@code exchange.setAttribute} —
     * that map is shared by every request on the context, so one user's form data would
     * be served to the next. See the note on {@link WebContext}.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> form(HttpExchange exchange) throws IOException {
        Object cached = WebContext.get(FORM_PARAMS);
        if (cached instanceof Map<?, ?> map) {
            return (Map<String, String>) map;
        }

        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        Map<String, String> params;
        if (contentType == null || !contentType.startsWith("application/x-www-form-urlencoded")) {
            params = Map.of();
        } else {
            try (InputStream in = exchange.getRequestBody()) {
                byte[] body = in.readNBytes(MAX_FORM_BYTES);
                params = parseUrlEncoded(new String(body, StandardCharsets.UTF_8));
            }
        }
        WebContext.put(FORM_PARAMS, params);
        return params;
    }

    /** A trimmed form field, or the empty string when absent. */
    public static String field(Map<String, String> form, String name) {
        String value = form.get(name);
        return value == null ? "" : value.trim();
    }

    /** A trimmed form field, or {@code null} when absent or blank. */
    public static String optionalField(Map<String, String> form, String name) {
        String value = field(form, name);
        return value.isEmpty() ? null : value;
    }

    public static boolean checkbox(Map<String, String> form, String name) {
        String value = form.get(name);
        return value != null && !value.isBlank() && !"false".equalsIgnoreCase(value)
                && !"off".equalsIgnoreCase(value);
    }

    private static Map<String, String> parseUrlEncoded(String raw) {
        Map<String, String> params = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return params;
        }
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String name = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            params.put(decode(name), decode(value));
        }
        return params;
    }

    private static String decode(String raw) {
        try {
            // '+' means space in form encoding; URLDecoder handles that.
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // A malformed percent-escape must not crash the request; treat it as literal.
            return raw;
        }
    }
}
