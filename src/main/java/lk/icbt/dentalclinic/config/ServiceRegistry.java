package lk.icbt.dentalclinic.config;

import lk.icbt.dentalclinic.dao.AppointmentDao;
import lk.icbt.dentalclinic.dao.BillDao;
import lk.icbt.dentalclinic.dao.ReportDao;
import lk.icbt.dentalclinic.dao.DentistDao;
import lk.icbt.dentalclinic.dao.HelpTopicDao;
import lk.icbt.dentalclinic.dao.PatientDao;
import lk.icbt.dentalclinic.dao.RoleDao;
import lk.icbt.dentalclinic.dao.SettingsDao;
import lk.icbt.dentalclinic.dao.TreatmentDao;
import lk.icbt.dentalclinic.dao.UserDao;
import lk.icbt.dentalclinic.dao.jdbc.ConnectionPool;
import lk.icbt.dentalclinic.dao.jdbc.JdbcAppointmentDao;
import lk.icbt.dentalclinic.dao.jdbc.JdbcBillDao;
import lk.icbt.dentalclinic.dao.jdbc.JdbcReportDao;
import lk.icbt.dentalclinic.dao.jdbc.JdbcDentistDao;
import lk.icbt.dentalclinic.dao.jdbc.JdbcHelpTopicDao;
import lk.icbt.dentalclinic.dao.jdbc.JdbcPatientDao;
import lk.icbt.dentalclinic.dao.jdbc.JdbcRoleDao;
import lk.icbt.dentalclinic.dao.jdbc.JdbcSettingsDao;
import lk.icbt.dentalclinic.dao.jdbc.JdbcTreatmentDao;
import lk.icbt.dentalclinic.dao.jdbc.JdbcUserDao;
import lk.icbt.dentalclinic.dao.jdbc.TransactionManager;
import lk.icbt.dentalclinic.event.AppointmentNotificationListener;
import lk.icbt.dentalclinic.event.EventBus;
import lk.icbt.dentalclinic.security.AccessRules;
import lk.icbt.dentalclinic.security.PasswordHasher;
import lk.icbt.dentalclinic.security.SessionManager;
import lk.icbt.dentalclinic.service.AppointmentAccessPolicy;
import lk.icbt.dentalclinic.service.AppointmentService;
import lk.icbt.dentalclinic.service.AuthService;
import lk.icbt.dentalclinic.service.BillingService;
import lk.icbt.dentalclinic.service.pricing.PricingStrategyFactory;
import lk.icbt.dentalclinic.service.RegistrationService;
import lk.icbt.dentalclinic.web.TemplateEngine;

/**
 * DEPENDENCY INJECTION — the whole object graph, wired in one readable place.
 *
 * <p>Every collaborator arrives through a constructor. No class reaches out for what it
 * needs, so each one's dependencies are visible in its signature and a test can supply
 * a stand-in without any framework at all.
 *
 * <p><strong>Evaluated against Spring, honestly.</strong> Spring would build this graph
 * by scanning for annotations, and this class would not exist. What is gained by writing
 * it out is that the graph is a thing you can read top to bottom in thirty lines:
 * construction order is explicit, there is no reflection, no proxying and no
 * classpath-scanning surprise, and startup failures are compile errors rather than
 * runtime {@code NoSuchBeanDefinitionException}s. What is lost is real: no scopes, no
 * lazy initialisation, no conditional wiring by profile, and every new dependency must
 * be added by hand. At this size the trade is clearly worth it; at a hundred services it
 * would not be, and saying so is part of the evaluation.
 *
 * <p>Construction order below is deliberate and follows the tiers: pool, then DAOs, then
 * services, then presentation.
 */
public final class ServiceRegistry {

    private final AppConfig config;

    // Infrastructure
    private final ConnectionPool connectionPool;
    private final TransactionManager transactionManager;

    // Data tier
    private final UserDao userDao;
    private final RoleDao roleDao;
    private final PatientDao patientDao;
    private final DentistDao dentistDao;
    private final TreatmentDao treatmentDao;
    private final HelpTopicDao helpTopicDao;
    private final SettingsDao settingsDao;
    private final AppointmentDao appointmentDao;
    private final BillDao billDao;
    private final ReportDao reportDao;

    // Cross-cutting
    private final PasswordHasher passwordHasher;
    private final SessionManager sessionManager;
    private final AccessRules accessRules;
    private final EventBus eventBus;
    private final AppointmentNotificationListener notificationListener;

    // Business tier
    private final AuthService authService;
    private final RegistrationService registrationService;
    private final AppointmentAccessPolicy appointmentAccessPolicy;
    private final AppointmentService appointmentService;
    private final PricingStrategyFactory pricingStrategyFactory;
    private final BillingService billingService;

    // Presentation tier
    private final TemplateEngine templateEngine;

    public ServiceRegistry(AppConfig config) {
        this.config = config;

        this.connectionPool = ConnectionPool.initialise(config);
        this.transactionManager = new TransactionManager(connectionPool);

        this.userDao = new JdbcUserDao(connectionPool);
        this.roleDao = new JdbcRoleDao(connectionPool);
        this.patientDao = new JdbcPatientDao(connectionPool);
        this.dentistDao = new JdbcDentistDao(connectionPool);
        this.treatmentDao = new JdbcTreatmentDao(connectionPool);
        this.helpTopicDao = new JdbcHelpTopicDao(connectionPool);
        this.settingsDao = new JdbcSettingsDao(connectionPool);
        this.appointmentDao = new JdbcAppointmentDao(connectionPool);
        this.billDao = new JdbcBillDao(connectionPool);
        this.reportDao = new JdbcReportDao(connectionPool);

        this.passwordHasher = new PasswordHasher();
        this.sessionManager = SessionManager.getInstance();
        this.accessRules = AccessRules.defaults();

        // OBSERVER: the bus and its listeners are wired here and nowhere else.
        // AppointmentService publishes without knowing this listener exists.
        this.eventBus = new EventBus();
        this.notificationListener = new AppointmentNotificationListener();
        this.eventBus.subscribe(notificationListener);

        this.authService = new AuthService(userDao, passwordHasher, sessionManager);
        this.registrationService = new RegistrationService(
                userDao, patientDao, roleDao, passwordHasher, transactionManager);
        this.appointmentAccessPolicy = new AppointmentAccessPolicy(patientDao, dentistDao);
        this.appointmentService = new AppointmentService(appointmentDao, patientDao,
                dentistDao, treatmentDao, settingsDao, appointmentAccessPolicy,
                transactionManager, eventBus);

        // STRATEGY + FACTORY METHOD. Adding a treatment family means adding a strategy
        // class to withDefaults() -- BillingService is untouched.
        this.pricingStrategyFactory = PricingStrategyFactory.withDefaults();
        this.billingService = new BillingService(billDao, appointmentDao, patientDao,
                settingsDao, pricingStrategyFactory, appointmentAccessPolicy,
                transactionManager, eventBus);

        this.templateEngine = new TemplateEngine(config.isDevelopment());
    }

    public AppConfig config() {
        return config;
    }

    public ConnectionPool connectionPool() {
        return connectionPool;
    }

    public TransactionManager transactionManager() {
        return transactionManager;
    }

    public UserDao userDao() {
        return userDao;
    }

    public RoleDao roleDao() {
        return roleDao;
    }

    public PatientDao patientDao() {
        return patientDao;
    }

    public DentistDao dentistDao() {
        return dentistDao;
    }

    public TreatmentDao treatmentDao() {
        return treatmentDao;
    }

    public HelpTopicDao helpTopicDao() {
        return helpTopicDao;
    }

    public SettingsDao settingsDao() {
        return settingsDao;
    }

    public PasswordHasher passwordHasher() {
        return passwordHasher;
    }

    public SessionManager sessionManager() {
        return sessionManager;
    }

    public AccessRules accessRules() {
        return accessRules;
    }

    public AuthService authService() {
        return authService;
    }

    public RegistrationService registrationService() {
        return registrationService;
    }

    public AppointmentDao appointmentDao() {
        return appointmentDao;
    }

    public AppointmentAccessPolicy appointmentAccessPolicy() {
        return appointmentAccessPolicy;
    }

    public AppointmentService appointmentService() {
        return appointmentService;
    }

    public BillDao billDao() {
        return billDao;
    }

    public ReportDao reportDao() {
        return reportDao;
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public AppointmentNotificationListener notificationListener() {
        return notificationListener;
    }

    public PricingStrategyFactory pricingStrategyFactory() {
        return pricingStrategyFactory;
    }

    public BillingService billingService() {
        return billingService;
    }

    public TemplateEngine templateEngine() {
        return templateEngine;
    }

    /** Releases the pooled connections and the event delivery pool. */
    public void shutdown() {
        // The bus first: a listener mid-delivery may still need a connection.
        eventBus.close();
        connectionPool.shutdown();
    }
}
