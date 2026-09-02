package lk.dentalclinic.web.handler;

import com.sun.net.httpserver.HttpExchange;
import lk.dentalclinic.web.Handler;
import lk.dentalclinic.web.Responses;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Liveness endpoint at {@code GET /health}.
 *
 * <p>Used by the M6 deployment platform's health check and by the M5 end-to-end tests,
 * which need to know the server is accepting connections before they start asserting.
 * M2 extends it to report connection-pool health.
 */
public final class HealthHandler implements Handler {

    private final Instant startedAt;
    private final String version;

    public HealthHandler(Instant startedAt, String version) {
        this.startedAt = startedAt;
        this.version = version;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        long uptimeSeconds = Duration.between(startedAt, Instant.now()).toSeconds();
        String body = """
                {"status":"UP","version":"%s","uptimeSeconds":%d}""".formatted(version, uptimeSeconds);
        Responses.json(exchange, 200, body);
    }
}
