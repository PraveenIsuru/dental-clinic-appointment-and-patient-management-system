package lk.dentalclinic;

import lk.dentalclinic.config.AppConfig;
import lk.dentalclinic.config.ServiceRegistry;
import lk.dentalclinic.web.HttpServerBootstrap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Billing end to end — brief requirement 4, "calculate the total treatment cost … and
 * print the patient bill/receipt".
 *
 * <p>The centrepiece is {@link #matchesTheSequenceDiagramWorkedExample()}, which asserts
 * the exact figures drawn in the Generate Bill sequence diagram. A design document and an
 * implementation that agree only in prose are not evidence; one that agrees to the rupee
 * is.
 *
 * <p>Also covers what must be <em>refused</em>: billing an appointment that is not
 * complete, billing one twice, and a discount above the 25% cap — the last of which is
 * enforced by {@code trg_bill_before_insert} as well as by the service.
 */
class BillingFlowIT {

    /**
     * Matches the page-wide meta tag, or a form's hidden field.
     *
     * <p>Both exist: every signed-in page carries the meta tag, and every form still
     * carries its own hidden input so the form works without JavaScript. A test that
     * only knew about the form field could not obtain a token from a page whose form is
     * conditionally hidden — which is how these four tests failed first time round.
     */
    private static final Pattern CSRF = Pattern.compile(
            "name=\"csrf-token\" content=\"([^\"]+)\"|name=\"_csrf\"\\s+value=\"([^\"]+)\"");
    private static final Pattern APPOINTMENT_NO = Pattern.compile("APT-\\d{4}-\\d{4}");
    private static final Pattern BILL_NO = Pattern.compile("BIL-\\d{4}-\\d{4}");

    private static HttpServerBootstrap server;
    private static ServiceRegistry registry;
    private static HttpClient client;
    private static boolean databaseAvailable;

    private static final LocalDate FUTURE = LocalDate.now().plusDays(60);
    private static final String TEST_ADDRESS = "BILLING-IT Test Road";

    @BeforeAll
    static void startApplication() throws IOException {
        AppConfig config = AppConfig.load(Path.of("config", "application.properties"));
        try {
            registry = new ServiceRegistry(config);
            registry.connectionPool().verifyConnectivity();
            databaseAvailable = true;
        } catch (SQLException | RuntimeException e) {
            System.out.println("BillingFlowIT skipped: database unavailable -- " + e.getMessage());
            databaseAvailable = false;
            return;
        }
        server = HttpServerBootstrap.start(0, 4, Main.buildRouter(registry, Instant.now()));
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        clearTestData();
    }

    @AfterAll
    static void stopApplication() {
        if (databaseAvailable) {
            clearTestData();
        }
        if (server != null) {
            server.close();
        }
        if (registry != null) {
            registry.shutdown();
        }
    }

    /** Bills reference appointments, which reference patients: delete in that order. */
    private static void clearTestData() {
        try (var connection = registry.connectionPool().borrow();
             var statement = connection.createStatement()) {

            statement.executeUpdate(
                    "DELETE FROM bills WHERE appointment_id IN "
                            + "(SELECT appointment_id FROM appointments "
                            + " WHERE appointment_date = '" + FUTURE + "')");
            statement.executeUpdate(
                    "DELETE FROM appointments WHERE appointment_date = '" + FUTURE + "'");
            statement.executeUpdate(
                    "DELETE FROM patients WHERE address = '" + TEST_ADDRESS + "'");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not clear test data", e);
        }
    }

    private static void requireDatabase() {
        assumeTrue(databaseAvailable, "MySQL is not running; start WAMP to run these tests");
    }

    // ------------------------------------------------------------------ plumbing

    private static HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server.baseUrl() + path))
                .timeout(Duration.ofSeconds(10)).GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, String body, String cookie)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server.baseUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String signIn(String username, String password) throws Exception {
        HttpResponse<String> response = post("/login",
                "username=" + username + "&password=" + enc(password), null);
        assertEquals(303, response.statusCode());
        return response.headers().allValues("Set-Cookie").stream()
                .filter(h -> h.startsWith("SESSIONID=") && !h.contains("Max-Age=0"))
                .map(h -> h.substring(0, h.indexOf(';')))
                .findFirst().orElseThrow(() -> new AssertionError("no session cookie"));
    }

    private static String csrfFrom(String html) {
        Matcher matcher = CSRF.matcher(html);
        assertTrue(matcher.find(), "the page should carry a CSRF token");
        return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Books an appointment for the given treatment and marks it complete, which is the
     * precondition for billing.
     *
     * @param treatmentId 5 is Root Canal Treatment at 25,000 in the seed data
     */
    private static String completedAppointment(String cookie, String time, int treatmentId,
                                               String patientName) throws Exception {
        String csrf = csrfFrom(get("/appointments/new", cookie).body());
        HttpResponse<String> booked = post("/appointments",
                "_csrf=" + enc(csrf)
                        + "&patientName=" + enc(patientName)
                        + "&address=" + enc(TEST_ADDRESS)
                        + "&contactNumber=0771234567"
                        + "&dentistId=1&treatmentId=" + treatmentId
                        + "&appointmentDate=" + FUTURE
                        + "&appointmentTime=" + enc(time), cookie);
        assertEquals(303, booked.statusCode(), booked.body());

        Matcher matcher = APPOINTMENT_NO.matcher(
                booked.headers().firstValue("Location").orElseThrow());
        assertTrue(matcher.find());
        String number = matcher.group();

        String detailCsrf = csrfFrom(get("/appointments/" + number, cookie).body());
        assertEquals(303, post("/appointments/" + number + "/complete",
                "_csrf=" + enc(detailCsrf), cookie).statusCode());
        return number;
    }

    private static String issueBill(String cookie, String appointmentNo, int quantity,
                                    String discountPercent) throws Exception {
        String csrf = csrfFrom(get("/bills/new?appointmentNo=" + appointmentNo, cookie).body());
        HttpResponse<String> response = post("/bills",
                "_csrf=" + enc(csrf) + "&appointmentNo=" + appointmentNo
                        + "&quantity=" + quantity + "&discountPercent=" + discountPercent, cookie);
        assertEquals(303, response.statusCode(), response.body());

        Matcher matcher = BILL_NO.matcher(response.headers().firstValue("Location").orElseThrow());
        assertTrue(matcher.find(), "the redirect should carry the bill number");
        return matcher.group();
    }

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("the bill matches the Generate Bill sequence diagram, to the rupee")
    void matchesTheSequenceDiagramWorkedExample() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");

        // Root canal, one canal, 10% discount — the diagram's worked example:
        //   consultation  2,500 + root canal 25,000 = 27,500 subtotal
        //   discount 10%  -2,750
        //   tax 0% (exempt)
        //   TOTAL         24,750
        String appointmentNo = completedAppointment(cookie, "09:00", 5, "Worked Example Patient");
        String billNo = issueBill(cookie, appointmentNo, 1, "10");

        String body = get("/bills/" + billNo, cookie).body();

        assertTrue(body.contains("2500.00"), "consultation fee: " + body);
        assertTrue(body.contains("25000.00"), "treatment charge");
        assertTrue(body.contains("27500.00"), "subtotal");
        assertTrue(body.contains("2750.00"), "10% discount");
        assertTrue(body.contains("24750.00"),
                "the total must be 24,750 exactly, as the sequence diagram draws it");
    }

    @Test
    @DisplayName("the root canal strategy prices three canals at 55,000, not 75,000")
    void strategyAppliesToTheRealBill() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");

        String appointmentNo = completedAppointment(cookie, "09:30", 5, "Three Canal Patient");
        String billNo = issueBill(cookie, appointmentNo, 3, "0");

        String body = get("/bills/" + billNo, cookie).body();

        // 25000 + 15000 + 15000 = 55000, plus the 2500 consultation = 57500.
        assertTrue(body.contains("55000.00"), "the tapered canal charge: " + body);
        assertTrue(body.contains("57500.00"), "total with the consultation fee");
        assertFalse(body.contains("75000.00"), "a flat multiple would be wrong");
    }

    @Test
    @DisplayName("the quotation shows the pricing rule that was applied")
    void quotationNamesThePricingRule() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");
        String appointmentNo = completedAppointment(cookie, "10:00", 3, "Filling Patient");

        String body = get("/bills/new?appointmentNo=" + appointmentNo + "&quantity=2", cookie)
                .body();

        assertTrue(body.contains("First surface at full rate"),
                "the Strategy's own rule text should reach the counter: " + body);
        // 7500 + 5250 = 12750
        assertTrue(body.contains("12750.00"), body);
    }

    @Test
    @DisplayName("an appointment that is not complete cannot be billed")
    void onlyCompletedAppointmentsAreBillable() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");

        // Booked but deliberately not completed.
        String csrf = csrfFrom(get("/appointments/new", cookie).body());
        HttpResponse<String> booked = post("/appointments",
                "_csrf=" + enc(csrf) + "&patientName=" + enc("Not Completed")
                        + "&address=" + enc(TEST_ADDRESS) + "&contactNumber=0771234567"
                        + "&dentistId=1&treatmentId=2&appointmentDate=" + FUTURE
                        + "&appointmentTime=10%3A30", cookie);
        Matcher matcher = APPOINTMENT_NO.matcher(
                booked.headers().firstValue("Location").orElseThrow());
        assertTrue(matcher.find());
        String appointmentNo = matcher.group();

        String billCsrf = csrfFrom(get("/bills/new", cookie).body());
        HttpResponse<String> refused = post("/bills",
                "_csrf=" + enc(billCsrf) + "&appointmentNo=" + appointmentNo
                        + "&quantity=1&discountPercent=0", cookie);

        assertEquals(409, refused.statusCode());
        assertTrue(refused.body().contains("Only a completed appointment can be billed"),
                refused.body());
    }

    @Test
    @DisplayName("an appointment cannot be billed twice")
    void doubleBillingIsRefused() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");
        String appointmentNo = completedAppointment(cookie, "11:00", 2, "Double Bill Patient");

        issueBill(cookie, appointmentNo, 1, "0");

        String csrf = csrfFrom(get("/bills/new", cookie).body());
        HttpResponse<String> second = post("/bills",
                "_csrf=" + enc(csrf) + "&appointmentNo=" + appointmentNo
                        + "&quantity=1&discountPercent=0", cookie);

        assertEquals(409, second.statusCode());
        assertTrue(second.body().contains("already been issued"), second.body());
    }

    @Test
    @DisplayName("a discount above 25% is refused; exactly 25% is accepted")
    void discountCapIsEnforced() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");

        String tooHigh = completedAppointment(cookie, "11:30", 2, "Over Cap Patient");
        String csrf = csrfFrom(get("/bills/new", cookie).body());
        HttpResponse<String> refused = post("/bills",
                "_csrf=" + enc(csrf) + "&appointmentNo=" + tooHigh
                        + "&quantity=1&discountPercent=40", cookie);

        assertEquals(422, refused.statusCode());
        assertTrue(refused.body().contains("may not exceed"), refused.body());

        // The boundary itself is allowed: 2500 + 5000 = 7500, 25% = 1875, total 5625.
        String atCap = completedAppointment(cookie, "12:00", 2, "At Cap Patient");
        String billNo = issueBill(cookie, atCap, 1, "25");
        String body = get("/bills/" + billNo, cookie).body();

        assertTrue(body.contains("1875.00"), "the capped discount: " + body);
        assertTrue(body.contains("5625.00"), "the total after the maximum discount");
    }

    @Test
    @DisplayName("the printable receipt carries the clinic details and no navigation")
    void receiptIsStandalone() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");
        String appointmentNo = completedAppointment(cookie, "12:30", 2, "Receipt Patient");
        String billNo = issueBill(cookie, appointmentNo, 1, "0");

        HttpResponse<String> response = get("/bills/" + billNo + "/receipt", cookie);

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("Sunrise Dental Clinic"), "the clinic name from settings");
        assertTrue(body.contains("221 Galle Road"), "the clinic address from settings");
        assertTrue(body.contains("Receipt Patient"));
        assertTrue(body.contains(billNo));
        assertTrue(body.contains("print.css"), "the print stylesheet must be linked");
        // A receipt is handed to a patient; site navigation has no place on it.
        assertFalse(body.contains("class=\"topbar\""),
                "the receipt must not carry the site navigation");
    }

    @Test
    @DisplayName("recording payment marks the bill paid and cannot be repeated")
    void paymentIsRecordedOnce() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");
        String appointmentNo = completedAppointment(cookie, "13:00", 2, "Payment Patient");
        String billNo = issueBill(cookie, appointmentNo, 1, "0");

        String csrf = csrfFrom(get("/bills/" + billNo, cookie).body());
        assertEquals(303, post("/bills/" + billNo + "/pay", "_csrf=" + enc(csrf), cookie)
                .statusCode());

        assertTrue(get("/bills/" + billNo, cookie).body().contains("PAID"));

        String secondCsrf = csrfFrom(get("/bills/" + billNo, cookie).body());
        HttpResponse<String> again =
                post("/bills/" + billNo + "/pay", "_csrf=" + enc(secondCsrf), cookie);
        assertEquals(409, again.statusCode());
        assertTrue(again.body().contains("already marked paid"), again.body());
    }

    @Test
    @DisplayName("a patient may see their own bill but not another patient's")
    void billAccessIsScoped() throws Exception {
        requireDatabase();
        String admin = signIn("admin", "Admin@123");
        String appointmentNo = completedAppointment(admin, "13:30", 2, "Someone Else Entirely");
        String billNo = issueBill(admin, appointmentNo, 1, "0");

        String patient = signIn("kasun.f", "Patient@123");
        HttpResponse<String> response = get("/bills/" + billNo, patient);

        assertEquals(404, response.statusCode(),
                "403 would confirm the bill exists; bill numbers are sequential");
        assertFalse(response.body().contains("Someone Else Entirely"));
    }

    @Test
    @DisplayName("a patient cannot issue a bill")
    void patientsCannotIssueBills() throws Exception {
        requireDatabase();
        String patient = signIn("kasun.f", "Patient@123");

        assertEquals(403, get("/bills/new", patient).statusCode());
    }

    @Test
    @DisplayName("the reports page runs all four reports and names the routines behind them")
    void reportsRunAgainstTheDatabase() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");
        String appointmentNo = completedAppointment(cookie, "14:00", 2, "Report Patient");
        issueBill(cookie, appointmentNo, 1, "0");

        HttpResponse<String> response = get("/admin/reports?date=" + LocalDate.now(), cookie);

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("Daily operations"));
        assertTrue(body.contains("Revenue by treatment"));
        assertTrue(body.contains("Dentist workload"));
        assertTrue(body.contains("Patient visit history"));
        // The reports are backed by the M1 routines, and the page says so.
        assertTrue(body.contains("sp_daily_revenue_report"));
        assertTrue(body.contains("vw_dentist_workload"));
        // The workload view returns real dentists.
        assertTrue(body.contains("Dr. Nimal Perera"), "vw_dentist_workload should return rows");
    }

    @Test
    @DisplayName("the daily revenue report includes the ROLLUP total row")
    void revenueReportHasRollupTotal() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");
        String appointmentNo = completedAppointment(cookie, "14:30", 2, "Rollup Patient");
        issueBill(cookie, appointmentNo, 1, "0");

        String body = get("/admin/reports?date=" + LocalDate.now(), cookie).body();

        assertTrue(body.contains("ALL TREATMENTS"),
                "sp_daily_revenue_report's WITH ROLLUP row should render as the footer");
        assertTrue(body.contains("total-row"));
    }

    @Test
    @DisplayName("a booking produces an asynchronous confirmation — the Observer really runs")
    void observerProducesAConfirmation() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");
        int before = registry.notificationListener().sentCount();

        completedAppointment(cookie, "15:00", 2, "Notified Patient");

        // Delivery is on a background pool, so wait briefly rather than assuming it ran.
        for (int attempt = 0; attempt < 50; attempt++) {
            if (registry.notificationListener().sentCount() > before) {
                break;
            }
            Thread.sleep(100);
        }

        assertTrue(registry.notificationListener().sentCount() > before,
                "the booking should have produced a confirmation");
        assertTrue(registry.notificationListener().recent().stream()
                        .anyMatch(n -> n.body().contains("Notified Patient")),
                "the message should name the patient");

        // And the administrator can see the evidence on the reports page.
        assertTrue(get("/admin/reports", cookie).body().contains("Notified Patient"));
    }

    @Test
    @DisplayName("a patient can see their own visit history with its bills")
    void patientVisitHistory() throws Exception {
        requireDatabase();
        String patient = signIn("kasun.f", "Patient@123");

        HttpResponse<String> response = get("/patient/history", patient);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Visit history"));
        // The seed gives Kasun a completed cleaning with a paid bill.
        assertTrue(response.body().contains("Scaling and Polishing"), response.body());
    }
}
