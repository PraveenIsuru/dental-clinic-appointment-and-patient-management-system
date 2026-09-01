package lk.icbt.dentalclinic;

import lk.icbt.dentalclinic.config.AppConfig;
import lk.icbt.dentalclinic.web.Router;
import lk.icbt.dentalclinic.web.HttpServerBootstrap;
import lk.icbt.dentalclinic.web.handler.HealthHandler;
import lk.icbt.dentalclinic.web.handler.StaticFileHandler;

import java.io.IOException;
import java.time.Instant;

/**
 * Application entry point for the Sunrise Dental Clinic appointment and patient
 * management system.
 *
 * <p>CIS6003 Advanced Programming, WRIT1. Plain Java, no application framework -
 * see {@code my-docs/PROJECT-PLAN.md} for the architecture and the rationale.
 */
public final class Main {

    public static final String VERSION = "1.0-SNAPSHOT";

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        Instant startedAt = Instant.now();
        AppConfig config = AppConfig.load();

        Router router = buildRouter(startedAt);
        HttpServerBootstrap server =
                HttpServerBootstrap.start(config.serverPort(), config.serverThreads(), router);

        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "shutdown-hook"));

        System.out.println("Sunrise Dental Clinic is running at " + server.baseUrl());
        System.out.println("Press Ctrl+C to stop.");
    }

    /**
     * Builds the routing table. Kept separate from {@link #main} so tests can start the
     * same routes on an ephemeral port.
     */
    public static Router buildRouter(Instant startedAt) {
        return new Router()
                .get("/health", new HealthHandler(startedAt, VERSION))
                // Registered last: the wildcard would otherwise shadow every route above it.
                .get("/**", new StaticFileHandler());
    }
}
