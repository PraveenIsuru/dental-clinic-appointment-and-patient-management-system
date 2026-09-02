package lk.dentalclinic;

import lk.dentalclinic.config.AppConfig;
import lk.dentalclinic.config.ServiceRegistry;
import lk.dentalclinic.web.HttpServerBootstrap;
import lk.dentalclinic.web.Router;
import lk.dentalclinic.web.View;
import lk.dentalclinic.web.filter.AuthorizationFilter;
import lk.dentalclinic.web.filter.CsrfFilter;
import lk.dentalclinic.web.filter.LoggingFilter;
import lk.dentalclinic.web.filter.SessionFilter;
import lk.dentalclinic.web.handler.AppointmentDetailHandler;
import lk.dentalclinic.web.handler.AppointmentHandler;
import lk.dentalclinic.web.handler.AvailabilityHandler;
import lk.dentalclinic.web.handler.BillingHandler;
import lk.dentalclinic.web.handler.DashboardHandler;
import lk.dentalclinic.web.handler.HealthHandler;
import lk.dentalclinic.web.handler.HelpHandler;
import lk.dentalclinic.web.handler.LoginHandler;
import lk.dentalclinic.web.handler.LogoutHandler;
import lk.dentalclinic.web.handler.RecordsHandler;
import lk.dentalclinic.web.handler.RegisterHandler;
import lk.dentalclinic.web.handler.ReportsHandler;
import lk.dentalclinic.web.handler.StaticFileHandler;
import lk.dentalclinic.web.handler.api.AppointmentApiHandler;
import lk.dentalclinic.web.handler.api.BillApiHandler;
import lk.dentalclinic.web.handler.api.CatalogApiHandler;
import lk.dentalclinic.web.handler.api.SessionApiHandler;

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
                registry.patientDao(), registry.dentistDao(), registry.treatmentDao(),
                registry.appointmentService(), view);

        AppointmentHandler appointments = new AppointmentHandler(registry.appointmentService(),
                registry.patientDao(), registry.dentistDao(), registry.treatmentDao(), view);
        AppointmentDetailHandler detail =
                new AppointmentDetailHandler(registry.appointmentService(), view);
        AvailabilityHandler availability = new AvailabilityHandler(
                registry.appointmentService(), registry.dentistDao(), view);

        BillingHandler bills = new BillingHandler(registry.billingService(),
                registry.appointmentService(), view);
        ReportsHandler reports = new ReportsHandler(registry.reportDao(), registry.patientDao(),
                registry.notificationListener(), view);

        // --- REST API, Task B requirement (i) ------------------------------
        // The same service objects the pages use, so there is one set of business
        // rules with two entry points rather than two implementations.
        AppointmentApiHandler appointmentApi =
                new AppointmentApiHandler(registry.appointmentService());
        CatalogApiHandler catalogApi = new CatalogApiHandler(registry.dentistDao(),
                registry.treatmentDao(), registry.patientDao());
        BillApiHandler billApi =
                new BillApiHandler(registry.billingService(), registry.reportDao());

        RecordsHandler patients = records(registry, RecordsHandler.Kind.PATIENTS, view);
        RecordsHandler dentists = records(registry, RecordsHandler.Kind.DENTISTS, view);
        RecordsHandler treatments = records(registry, RecordsHandler.Kind.TREATMENTS, view);

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

                // Appointments. Order matters within this block: the literal segments
                // "new" and "search" must be registered before "{no}", or the wildcard
                // route would swallow them and try to look up an appointment called
                // "new". Routes match in registration order.
                .get("/appointments/new", appointments.newForm())
                .get("/appointments/search", detail.search())
                .get("/appointments", appointments)
                .post("/appointments", appointments)
                .get("/appointments/{no}", detail)
                .post("/appointments/{no}/{action}", detail.action())

                .get("/availability", availability)

                // Billing. As with appointments, the literal "new" must precede "{no}".
                .get("/bills/new", bills.quoteForm())
                .get("/bills", bills)
                .post("/bills", bills)
                .get("/bills/{no}", bills.detail())
                .get("/bills/{no}/receipt", bills.receipt())
                .post("/bills/{no}/pay", bills.pay())

                .get("/admin/reports", reports)
                .get("/patient/history", reports.myHistory())

                // ---------------------------------------------------------------
                // REST API v1. Literal segments before {parameter} ones, as above.
                // ---------------------------------------------------------------
                // The reference is a static page; this alias gives it the extensionless
                // address the documentation and the Postman collection both cite.
                .get("/api-docs", ex -> lk.dentalclinic.web.Responses
                        .redirect(ex, "/api-docs.html"))

                .get("/api/v1/session", SessionApiHandler.current())

                .get("/api/v1/dentists", catalogApi.dentists())
                .get("/api/v1/dentists/{id}/availability", appointmentApi.availability())
                .get("/api/v1/treatments", catalogApi.treatments())
                .get("/api/v1/patients", catalogApi.patients())

                .get("/api/v1/appointments", appointmentApi.list())
                .post("/api/v1/appointments", appointmentApi.create())
                .get("/api/v1/appointments/{no}", appointmentApi.get())
                .post("/api/v1/appointments/{no}/{action}", appointmentApi.action())

                .get("/api/v1/reports/daily", billApi.dailyReport())
                .get("/api/v1/reports/revenue", billApi.revenueReport())
                .get("/api/v1/reports/workload", billApi.workloadReport())

                .get("/api/v1/bills", billApi.list())
                .post("/api/v1/bills", billApi.create())
                .get("/api/v1/bills/{no}", billApi.get())
                .post("/api/v1/bills/{no}/pay", billApi.pay())

                // Administrator record management.
                .get("/admin/patients", patients)
                .post("/admin/patients", patients)
                .get("/admin/dentists", dentists)
                .post("/admin/dentists", dentists)
                .get("/admin/treatments", treatments)
                .post("/admin/treatments", treatments)

                // Registered last: the wildcard would otherwise shadow every route above it.
                .get("/**", new StaticFileHandler());
    }

    private static RecordsHandler records(ServiceRegistry registry, RecordsHandler.Kind kind,
                                          View view) {
        return new RecordsHandler(kind, registry.recordsService(), view);
    }
}
