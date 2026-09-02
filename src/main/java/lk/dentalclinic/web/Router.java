package lk.dentalclinic.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FRONT CONTROLLER pattern.
 *
 * <p>Every request entering the application passes through this single object, which
 * selects a {@link Handler} by HTTP method and path. The alternative - registering a
 * separate {@code HttpContext} per URL - scatters routing decisions across the codebase
 * and gives no single place to hang cross-cutting concerns. The filter chain added in
 * M2 wraps this one object.
 *
 * <p>Supported path patterns:
 * <ul>
 *   <li>{@code /health} - exact match</li>
 *   <li>{@code /appointments/{no}} - single-segment capture, read with
 *       {@link #pathParam(HttpExchange, String)}</li>
 *   <li>{@code /assets/**} - trailing wildcard, captures the remainder as {@code **}</li>
 * </ul>
 *
 * <p>Routes are matched in registration order, so register specific patterns before
 * wildcards.
 */
public final class Router implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(Router.class.getName());
    private static final String PATH_PARAMS = "lk.dentalclinic.pathParams";

    private final List<Route> routes = new ArrayList<>();
    private final List<Filter> filters = new ArrayList<>();
    private Handler notFoundHandler = ex -> Responses.html(ex, 404, Pages.notFound());
    private Handler errorHandler = ex -> Responses.html(ex, 500, Pages.serverError());

    public Router get(String pattern, Handler handler) {
        return register("GET", pattern, handler);
    }

    public Router post(String pattern, Handler handler) {
        return register("POST", pattern, handler);
    }

    public Router put(String pattern, Handler handler) {
        return register("PUT", pattern, handler);
    }

    public Router delete(String pattern, Handler handler) {
        return register("DELETE", pattern, handler);
    }

    public Router register(String method, String pattern, Handler handler) {
        routes.add(new Route(method.toUpperCase(), split(pattern), handler));
        return this;
    }

    public Router notFound(Handler handler) {
        this.notFoundHandler = handler;
        return this;
    }

    public Router onError(Handler handler) {
        this.errorHandler = handler;
        return this;
    }

    /**
     * Registers the filter chain wrapped around every request, including the ones that
     * end in 404 or 405 — so an unmatched path is still logged, and an unauthenticated
     * request to a mistyped protected URL is still redirected rather than leaking the
     * fact that no such page exists.
     */
    public Router filters(Filter... chain) {
        this.filters.addAll(List.of(chain));
        return this;
    }

    public int routeCount() {
        return routes.size();
    }

    public int filterCount() {
        return filters.size();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Filter.chain(filters, this::dispatch).handle(exchange);
        } catch (RuntimeException | IOException e) {
            LOG.log(Level.SEVERE, "Filter chain failed for " + exchange.getRequestURI(), e);
            safely(exchange, errorHandler);
        } finally {
            // Must run for every request, including failures. Worker threads are pooled
            // and reused, so leaving the session in the thread-local would hand it to
            // whichever request the thread picks up next.
            WebContext.clear();
            exchange.close();
        }
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        // HEAD is served by the GET handler; Responses suppresses the body.
        String lookupMethod = "HEAD".equals(method) ? "GET" : method;
        String[] path = split(exchange.getRequestURI().getPath());

        Set<String> allowedForPath = new LinkedHashSet<>();

        try {
            for (Route route : routes) {
                Map<String, String> params = route.match(path);
                if (params == null) {
                    continue;
                }
                if (route.method.equals(lookupMethod)) {
                    // WebContext, not exchange.setAttribute: that map is context-wide
                    // and would carry one request's path variables into the next.
                    WebContext.put(PATH_PARAMS, params);
                    route.handler.handle(exchange);
                    return;
                }
                allowedForPath.add(route.method);
            }

            if (!allowedForPath.isEmpty()) {
                allowedForPath.add("HEAD");
                exchange.getResponseHeaders().set("Allow", String.join(", ", allowedForPath));
                Responses.text(exchange, 405, "405 Method Not Allowed");
                return;
            }

            notFoundHandler.handle(exchange);
        } catch (RuntimeException | IOException e) {
            LOG.log(Level.SEVERE,
                    "Unhandled failure for " + method + " " + exchange.getRequestURI(), e);
            safely(exchange, errorHandler);
        }
    }

    /** Last-resort invocation: the response may already be committed, so failure here is ignorable. */
    private static void safely(HttpExchange exchange, Handler handler) {
        try {
            handler.handle(exchange);
        } catch (RuntimeException | IOException ignored) {
            // Nothing useful left to do; the original failure is already logged.
        }
    }

    /** Returns a captured path variable, or {@code null} if the route did not declare it. */
    public static String pathParam(HttpExchange exchange, String name) {
        Object raw = WebContext.get(PATH_PARAMS);
        if (raw instanceof Map<?, ?> map) {
            Object value = map.get(name);
            return value == null ? null : value.toString();
        }
        return null;
    }

    private static String[] split(String path) {
        String trimmed = path == null ? "" : path;
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/");
    }

    /** One registered route. Package-private: the routing table is an implementation detail. */
    private record Route(String method, String[] pattern, Handler handler) {

        /** Returns captured variables when the path matches, or {@code null} when it does not. */
        Map<String, String> match(String[] path) {
            Map<String, String> params = new LinkedHashMap<>();

            for (int i = 0; i < pattern.length; i++) {
                String segment = pattern[i];

                if ("**".equals(segment)) {
                    params.put("**", String.join("/",
                            java.util.Arrays.copyOfRange(path, Math.min(i, path.length), path.length)));
                    return params;
                }
                if (i >= path.length) {
                    return null;
                }
                if (segment.startsWith("{") && segment.endsWith("}")) {
                    params.put(segment.substring(1, segment.length() - 1), decode(path[i]));
                } else if (!segment.equals(path[i])) {
                    return null;
                }
            }

            return pattern.length == path.length ? params : null;
        }

        private static String decode(String raw) {
            return java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
