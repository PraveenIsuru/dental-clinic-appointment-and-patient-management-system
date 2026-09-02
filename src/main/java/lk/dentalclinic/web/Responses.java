package lk.dentalclinic.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Small helpers for writing a response body with the correct headers. */
public final class Responses {

    private Responses() {
    }

    public static void text(HttpExchange exchange, int status, String body) throws IOException {
        send(exchange, status, "text/plain; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    public static void html(HttpExchange exchange, int status, String body) throws IOException {
        send(exchange, status, "text/html; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    public static void json(HttpExchange exchange, int status, String body) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Sends a 303 See Other.
     *
     * <p>303 rather than 302 after a successful form post: it tells the browser to
     * follow up with a GET, so refreshing the destination does not re-submit the form
     * and book a second appointment.
     */
    public static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        send(exchange, 303, "text/plain; charset=utf-8", new byte[0]);
    }

    public static void send(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        // Defence-in-depth headers. Cheap to add, and they support the brief's
        // ETHICAL/DIGITAL criterion on secure coding practices.
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Referrer-Policy", "same-origin");

        boolean bodyAllowed = status != 204 && status != 304
                && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod());

        if (bodyAllowed) {
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } else {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        }
    }
}
