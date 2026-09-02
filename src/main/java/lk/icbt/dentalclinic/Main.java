package lk.icbt.dentalclinic;

import lk.icbt.dentalclinic.config.AppConfig;
import lk.icbt.dentalclinic.config.ServiceRegistry;
import lk.icbt.dentalclinic.web.HttpServerBootstrap;
import lk.icbt.dentalclinic.web.Router;
import lk.icbt.dentalclinic.web.View;
import lk.icbt.dentalclinic.web.filter.AuthorizationFilter;
import lk.icbt.dentalclinic.web.filter.CsrfFilter;
import lk.icbt.dentalclinic.web.filter.LoggingFilter;
import lk.icbt.dentalclinic.web.filter.SessionFilter;
import lk.icbt.dentalclinic.web.handler.DashboardHandler;
import lk.icbt.dentalclinic.web.handler.HealthHandler;
import lk.icbt.dentalclinic.web.handler.HelpHandler;
import lk.icbt.dentalclinic.web.handler.LoginHandler;
import lk.icbt.dentalclinic.web.handler.LogoutHandler;
import lk.icbt.dentalclinic.web.handler.RegisterHandler;
import lk.icbt.dentalclinic.web.handler.StaticFileHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Application entry point for the Sunrise Dental Clinic appointment and patient
 * management system.
 *
 * <p>CIS6003 Advanced Programming, WRIT1. Plain Java, no application framework —
 * see {@code my-docs/PROJECT-PLAN.md} for the architecture and the rationale.
 */
public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static final String VERSION = "1.0-SNAPSHOT";

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        Instant startedAt = Instant.now();
        AppConfig config = AppConfig.load();
        ServiceRegistry registry = new ServiceRegistry(config);

        verifyDatabase(registry);

        Router router = buildRouter(registry, startedAt);
        HttpServerBootstrap server =
                HttpServerBootstrap.start(config.serverPort(), config.serverThreads(), router);

        ScheduledExecutorService housekeeping = startSessionReaper(registry);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            housekeeping.shutdownNow();
            registry.shutdown();
        }, "shutdown-hook"));

        System.out.println("Sunrise Dental Clinic is running at " + server.baseUrl());
        System.out.println("Press Ctrl+C to stop.");
    }

    /**
     * Fails fast if the database is unreachable.
     *
     * <p>Better to refuse to start with a clear message than to serve a login page that
     * throws on every attempt, which looks like a bug in the application rather than a
     * database that is not running.
     */
    private static void verifyDatabase(ServiceRegistry registry) {
        try {
            registry.connectionPool().verifyConnectivity();
            LOG.info("Database connection verified");
        } catch (SQLException e) {
            throw new IllegalStateException("""
                    Cannot reach the database.
                      - Is MySQL running? (WAMP tray icon, MySQL, Service administration, Start)
                      - Have the migrations been applied? See database/V1__schema.sql
                      - Check db.url, db.user and db.password in config/application.properties
                    """, e);
        }
    }

    /** Discards idle sessions periodically so a long-running process does not leak them. */
    private static ScheduledExecutorService startSessionReaper(ServiceRegistry registry) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "session-reaper");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(
                () -> registry.sessionManager().purgeExpired(), 5, 5, TimeUnit.MINUTES);
        return executor;
    }

    /**
     * Builds the routing table and the filter chain.
     *
     * <p>Kept separate from {@link #main} so tests can start the same application on an
     * ephemeral port.
     */
    public static Router buildRouter(ServiceRegistry registry, Instant startedAt) {
        View view = new View(registry.templateEngine());

        LoginHandler login = new LoginHandler(registry.authService(), view, registry.config());
        LogoutHandler logout = new LogoutHandler(registry.authService());
        RegisterHandler register = new RegisterHandler(registry.registrationService(), view);
        HelpHandler help = new HelpHandler(registry.helpTopicDao(), view);
        DashboardHandler dashboard = new DashboardHandler(
                registry.patientDao(), registry.dentistDao(), registry.treatmentDao(), view);

        return new Router()
                // Order matters: logging wraps everything so it times the whole request;
                // the session must be resolved before authorisation can consult it; CSRF
                // runs last because it needs the session and only guards handlers.
                .filters(new LoggingFilter(),
                        new SessionFilter(registry.sessionManager()),
                        new AuthorizationFilter(registry.accessRules()),
                        new CsrfFilter())

                .get("/health", new HealthHandler(startedAt, VERSION))

                .get("/login", login)
                .post("/login", login)
                .get("/logout", logout)
                .post("/logout", logout)
                .get("/register", register)
                .post("/register", register)
                .get("/help", help)

                .get("/admin/dashboard", dashboard)
                .get("/dentist/dashboard", dashboard)
                .get("/patient/dashboard", dashboard)

                // Registered last: the wildcard would otherwise shadow every route above it.
                .get("/**", new StaticFileHandler());
    }
}
