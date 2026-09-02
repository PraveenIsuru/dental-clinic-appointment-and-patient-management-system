package lk.dentalclinic.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;

/**
 * CHAIN OF RESPONSIBILITY — one cross-cutting concern applied to every request.
 *
 * <p>A filter either handles the exchange itself (rejecting it) or calls
 * {@code next.handle(exchange)} to pass it on. Because each filter decides whether the
 * chain continues, authorisation can stop a request before the handler ever runs, and
 * no handler needs a line of authentication code.
 *
 * <p>This is what Spring provides through servlet filters and AOP. Composing plain
 * lambdas instead keeps the whole mechanism in the twenty lines of {@link #chain}, at
 * the cost of the interception being positional — order is defined by the list passed
 * to the router and nothing enforces it.
 */
@FunctionalInterface
public interface Filter {

    void handle(HttpExchange exchange, Handler next) throws IOException;

    /**
     * Folds filters around a handler so the first in the list runs first.
     *
     * <p>Built back to front: each filter is wrapped around everything after it, so
     * the returned {@link Handler} is a single object with the whole chain inside it
     * and no per-request chain state to reset.
     */
    static Handler chain(List<Filter> filters, Handler target) {
        Handler composed = target;
        for (int i = filters.size() - 1; i >= 0; i--) {
            Filter filter = filters.get(i);
            Handler next = composed;
            composed = exchange -> filter.handle(exchange, next);
        }
        return composed;
    }
}
