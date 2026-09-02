package lk.icbt.dentalclinic.web.filter;

import com.sun.net.httpserver.HttpExchange;
import lk.icbt.dentalclinic.web.Filter;
import lk.icbt.dentalclinic.web.Handler;
import lk.icbt.dentalclinic.web.WebContext;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Access log. First in the chain, so it times everything after it including the
 * filters.
 *
 * <p>Logs the username but never the session identifier, and never query strings on
 * authentication routes — a log file is a place credentials leak from, and it is
 * typically read by more people than the database is.
 */
public final class LoggingFilter implements Filter {

    private static final Logger LOG = Logger.getLogger("access");

    @Override
    public void handle(HttpExchange exchange, Handler next) throws IOException {
        long startedAt = System.nanoTime();
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            next.handle(exchange);
        } finally {
            long millis = (System.nanoTime() - startedAt) / 1_000_000;
            int status = exchange.getResponseCode();
            String who = WebContext.session()
                    .map(session -> session.getUsername() + "/" + session.getRole())
                    .orElse("anonymous");
            LOG.info(() -> String.format("%-6s %-38s %d  %4dms  %s",
                    method, path, status, millis, who));
        }
    }
}
