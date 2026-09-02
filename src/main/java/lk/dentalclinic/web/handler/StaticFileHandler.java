package lk.dentalclinic.web.handler;

import com.sun.net.httpserver.HttpExchange;
import lk.dentalclinic.web.Handler;
import lk.dentalclinic.web.Responses;
import lk.dentalclinic.web.Router;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Serves the hand-written HTML, CSS and JavaScript front end from the classpath.
 *
 * <p>No templating library and no asset pipeline: the whole front end is static files
 * plus {@code fetch()} calls against the API. M2 adds a minimal {@code {{placeholder}}}
 * template engine for server-rendered pages that need session data.
 */
public final class StaticFileHandler implements Handler {

    private static final String ROOT = "/static";
    private static final String INDEX = "index.html";

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "application/javascript; charset=utf-8",
            "json", "application/json; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "jpg", "image/jpeg",
            "ico", "image/x-icon",
            "woff2", "font/woff2");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requested = Router.pathParam(exchange, "**");
        if (requested == null || requested.isBlank()) {
            requested = INDEX;
        }
        if (requested.endsWith("/")) {
            requested = requested + INDEX;
        }

        String resource = ROOT + "/" + requested;

        // Reject traversal before touching the classpath. A request for
        // /../../application.properties must not escape the static root.
        if (requested.contains("..") || requested.contains("\\") || requested.startsWith("/")) {
            Responses.text(exchange, 400, "400 Bad Request");
            return;
        }

        try (InputStream in = StaticFileHandler.class.getResourceAsStream(resource)) {
            if (in == null) {
                Responses.html(exchange, 404, notFoundPage(requested));
                return;
            }
            byte[] body = in.readAllBytes();
            Responses.send(exchange, 200, contentTypeOf(requested), body);
        }
    }

    private static String contentTypeOf(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) {
            return "application/octet-stream";
        }
        String ext = path.substring(dot + 1).toLowerCase();
        return CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
    }

    private static String notFoundPage(String requested) {
        return """
                <!doctype html>
                <html lang="en">
                <head><meta charset="utf-8"><title>Not found</title>
                <link rel="stylesheet" href="/css/app.css"></head>
                <body class="centred">
                  <main class="card">
                    <h1>404</h1>
                    <p>No page at <code>%s</code>.</p>
                    <p><a href="/">Back to the clinic home page</a></p>
                  </main>
                </body>
                </html>
                """.formatted(escape(requested));
    }

    private static String escape(String raw) {
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
