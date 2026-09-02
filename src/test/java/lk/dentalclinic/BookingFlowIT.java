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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The appointment lifecycle over real HTTP against the real schema — brief requirements
 * 2 and 3.
 *
 * <p>The rules this proves cannot be proved by unit tests, because each depends on the
 * database: the appointment number comes from a stored procedure, the double-booking
 * refusal from a unique index, and the freeing of a cancelled slot from a generated
 * column. Skipped rather than failed when MySQL is absent, for the reasons given in
 * {@code LoginFlowIT}.
 *
 * <p>Every test books into a far-future date so it cannot collide with the seed data or
 * with a developer clicking around the running application.
 */
class BookingFlowIT {

    private static final Pattern CSRF =
            Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
    private static final Pattern APPOINTMENT_NO =
            Pattern.compile("APT-\\d{4}-\\d{4}");

    private static HttpServerBootstrap server;
    private static ServiceRegistry registry;
    private static HttpClient client;
    private static boolean databaseAvailable;

    /** Far enough ahead that the seeded rows and manual testing cannot reach it. */
    private static final LocalDate FUTURE = LocalDate.now().plusDays(45);

    /**
     * Stamped on every patient these tests create, so {@link #clearTestData()} can find
     * and remove them without touching the seed data.
     */
    private static final String TEST_ADDRESS = "IT-TEST 14/3 Temple Road";

    @BeforeAll
    static void startApplication() throws IOException {
        AppConfig config = AppConfig.load(Path.of("config", "application.properties"));
        try {
            registry = new ServiceRegistry(config);
            registry.connectionPool().verifyConnectivity();
            databaseAvailable = true;
        } catch (SQLException | RuntimeException e) {
            System.out.println("BookingFlowIT skipped: database unavailable -- " + e.getMessage());
            databaseAvailable = false;
            return;
        }
        server = HttpServerBootstrap.start(0, 4, Main.buildRouter(registry, Instant.now()));
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        // These tests write real rows and the slots they book stay booked. Without this
        // the suite passes once and fails on every subsequent run, because the second
        // run's bookings collide with the first run's -- which is exactly the rule under
        // test firing correctly against stale data. Clearing up front makes the suite
        // repeatable; clearing again afterwards leaves the database as it was found.
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

    /**
     * Removes everything these tests create: appointments on the test date, the patients
     * stamped with {@link #TEST_ADDRESS}, and treatments added by
     * {@code adminCanManageTreatments}.
     *
     * <p>Deletes in dependency order — appointments reference patients and treatments, so
     * removing a patient first would be refused by the foreign key. Raw JDBC rather than a
     * DAO on purpose: the application has no delete-appointment operation and should not
     * grow one merely to satisfy a test.
     */
    private static void clearTestData() {
        try (var connection = registry.connectionPool().borrow();
             var statement = connection.createStatement()) {

            statement.executeUpdate(
                    "DELETE FROM appointments WHERE appointment_date = '" + FUTURE + "'");
            statement.executeUpdate(
                    "DELETE FROM appointments WHERE patient_id IN "
                            + "(SELECT patient_id FROM patients WHERE address = '"
                            + TEST_ADDRESS + "')");
            statement.executeUpdate(
                    "DELETE FROM patients WHERE address = '" + TEST_ADDRESS + "'");
            statement.executeUpdate(
                    "DELETE FROM treatments WHERE code LIKE 'IT%' AND treatment_id NOT IN "
                            + "(SELECT DISTINCT treatment_id FROM appointments)");
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
        assertEquals(303, response.statusCode(), "sign-in should redirect");
        return response.headers().allValues("Set-Cookie").stream()
                .filter(h -> h.startsWith("SESSIONID=") && !h.contains("Max-Age=0"))
                .map(h -> h.substring(0, h.indexOf(';')))
                .findFirst().orElseThrow(() -> new AssertionError("no session cookie"));
    }

    /** Reads the CSRF token out of a rendered form; every state-changing POST needs it. */
    private static String csrfFrom(String html) {
        Matcher matcher = CSRF.matcher(html);
        assertTrue(matcher.find(), "the page should carry a CSRF token");
        return matcher.group(1);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Books an appointment as the administrator and returns its number. */
    private static String book(String cookie, String time, String patientName) throws Exception {
        String csrf = csrfFrom(get("/appointments/new", cookie).body());
        String body = "_csrf=" + enc(csrf)
                + "&patientName=" + enc(patientName)
                + "&address=" + enc(TEST_ADDRESS)
                + "&contactNumber=0771234567"
                + "&dentistId=1&treatmentId=2"
                + "&appointmentDate=" + FUTURE
                + "&appointmentTime=" + enc(time);

        HttpResponse<String> response = post("/appointments", body, cookie);
        assertEquals(303, response.statusCode(),
                "a valid booking should redirect; body was: " + response.body());

        String location = response.headers().firstValue("Location").orElseThrow();
        Matcher matcher = APPOINTMENT_NO.matcher(location);
        assertTrue(matcher.find(), "the redirect should carry the appointment number: " + location);
        return matcher.group();
    }

    private static Optional<String> tryBook(String cookie, String time) throws Exception {
        String csrf = csrfFrom(get("/appointments/new", cookie).body());
        String body = "_csrf=" + enc(csrf)
                + "&patientName=" + enc("Clash Test")
                + "&address=" + enc(TEST_ADDRESS)
                + "&contactNumber=0771234567"
                + "&dentistId=1&treatmentId=2"
                + "&appointmentDate=" + FUTURE
                + "&appointmentTime=" + enc(time);
        HttpResponse<String> response = post("/appointments", body, cookie);
        return response.statusCode() == 303
                ? response.headers().firstValue("Location")
                : Optional.empty();
    }

    private static void act(String cookie, String number, String action) throws Exception {
        String csrf = csrfFrom(get("/appointments/" + number, cookie).body());
        HttpResponse<String> response =
                post("/appointments/" + number + "/" + action, "_csrf=" + enc(csrf), cookie);
        assertEquals(303, response.statusCode(),
                action + " should redirect; body was: " + response.body());
    }

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("registering an appointment allocates an APT-<year>-<0000> number")
    void bookingAllocatesAppointmentNumber() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");

        String number = book(cookie, "09:00", "Booking Test Patient");

        assertTrue(number.startsWith("APT-" + FUTURE.getYear() + "-"), number);

        HttpResponse<String> detail = get("/appointments/" + number, cookie);
        assertEquals(200, detail.statusCode());
        assertTrue(detail.body().contains("Booking Test Patient"));
        assertTrue(detail.body().contains("Scaling and Polishing"));
    }

    @Test
    @DisplayName("search by appointment number is case-insensitive — requirement 3")
    void searchIsCaseInsensitive() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");
        String number = book(cookie, "09:30", "Search Test Patient");

        HttpResponse<String> found =
                get("/appointments/search?q=" + enc(number.toLowerCase()), cookie);

        assertEquals(303, found.statusCode(), "a hit should redirect to the detail page");
        assertTrue(found.headers().firstValue("Location").orElseThrow().contains(number));
    }

    @Test
    @DisplayName("an unknown appointment number reports not found, not an error")
    void searchMissReportsNotFound() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");

        HttpResponse<String> response = get("/appointments/search?q=APT-1999-9999", cookie);

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("No appointment found"), response.body());
    }

    @Test
    @DisplayName("the same dentist, date and time cannot be booked twice")
    void doubleBookingIsRefused() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");
        book(cookie, "10:00", "First Patient");

        String csrf = csrfFrom(get("/appointments/new", cookie).body());
        HttpResponse<String> second = post("/appointments",
                "_csrf=" + enc(csrf) + "&patientName=" + enc("Second Patient")
                        + "&address=" + enc(TEST_ADDRESS) + "&contactNumber=0779876543"
                        + "&dentistId=1&treatmentId=2&appointmentDate=" + FUTURE
                        + "&appointmentTime=10%3A00", cookie);

        assertEquals(409, second.statusCode(), "the clash should be refused");
        assertTrue(second.body().contains("already booked"), second.body());
        // The refusal must be useful, not merely correct.
        assertTrue(second.body().contains("next free times"),
                "the refusal should suggest alternatives: " + second.body());
    }

    @Test
    @DisplayName("cancelling frees the slot so it can be booked again")
    void cancellingFreesTheSlot() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");

        String first = book(cookie, "11:00", "Cancel Test One");
        // The slot is taken, so a second booking fails.
        assertTrue(tryBook(cookie, "11:00").isEmpty(), "the slot should be occupied");

        act(cookie, first, "cancel");

        // V4__cancelled_slots.sql: slot_active becomes NULL, so the row leaves
        // uq_dentist_slot and the time is bookable again.
        assertTrue(tryBook(cookie, "11:00").isPresent(),
                "a cancelled slot must be reusable, or every cancellation destroys a slot");
    }

    @Test
    @DisplayName("an appointment can be confirmed and then completed")
    void statusLifecycle() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");
        String number = book(cookie, "12:00", "Lifecycle Test");

        act(cookie, number, "confirm");
        assertTrue(get("/appointments/" + number, cookie).body().contains("CONFIRMED"));

        act(cookie, number, "complete");
        assertTrue(get("/appointments/" + number, cookie).body().contains("COMPLETED"));
    }

    @Test
    @DisplayName("rescheduling moves the appointment and frees the old slot")
    void rescheduleMovesTheAppointment() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");
        String number = book(cookie, "13:00", "Reschedule Test");

        String csrf = csrfFrom(get("/appointments/" + number, cookie).body());
        HttpResponse<String> moved = post("/appointments/" + number + "/reschedule",
                "_csrf=" + enc(csrf) + "&appointmentDate=" + FUTURE
                        + "&appointmentTime=13%3A30", cookie);

        assertEquals(303, moved.statusCode(), moved.body());
        assertTrue(get("/appointments/" + number, cookie).body().contains("13:30"));
        // The vacated 13:00 is available again.
        assertTrue(tryBook(cookie, "13:00").isPresent(), "the old slot should be free");
    }

    @Test
    @DisplayName("A6: a patient asking for another patient's appointment gets 404, not 403")
    void patientCannotSeeAnotherPatientsAppointment() throws Exception {
        requireDatabase();
        String admin = signIn("admin", "Admin@123");
        String number = book(admin, "14:00", "Somebody Else");

        String patient = signIn("kasun.f", "Patient@123");
        HttpResponse<String> response = get("/appointments/" + number, patient);

        assertEquals(404, response.statusCode(),
                "403 would confirm the appointment exists, letting numbers be enumerated");
        assertNotEquals(403, response.statusCode());
        assertFalse(response.body().contains("Somebody Else"));
    }

    @Test
    @DisplayName("a patient sees only their own appointments in the list")
    void patientListIsScoped() throws Exception {
        requireDatabase();
        String admin = signIn("admin", "Admin@123");
        book(admin, "14:30", "Not The Signed In Patient");

        String patient = signIn("kasun.f", "Patient@123");
        String body = get("/appointments", patient).body();

        assertFalse(body.contains("Not The Signed In Patient"),
                "another patient's booking must not appear in this list");
    }

    @Test
    @DisplayName("a dentist may not reach the administrator's record pages")
    void dentistCannotManageRecords() throws Exception {
        requireDatabase();
        String dentist = signIn("dr.perera", "Dentist@123");

        assertEquals(403, get("/admin/patients", dentist).statusCode());
        assertEquals(403, get("/admin/treatments", dentist).statusCode());
    }

    @Test
    @DisplayName("the availability grid marks free, booked and off-duty slots")
    void availabilityGridReflectsBookings() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");
        book(cookie, "15:00", "Availability Test");

        String body = get("/availability?dentistId=1&date=" + FUTURE, cookie).body();

        assertTrue(body.contains("slot booked"), "the booked slot should be marked");
        assertTrue(body.contains("slot free"), "free slots should be clickable");
        // Dr Perera works 08:00-16:00, so the evening is off duty.
        assertTrue(body.contains("slot off"), "hours outside the session should be marked");
        assertTrue(body.contains("Availability Test"), "the grid names who holds a slot");
    }

    @Test
    @DisplayName("validation refuses a past date and an off-boundary time, listing both")
    void validationRefusesBadInput() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");
        String csrf = csrfFrom(get("/appointments/new", cookie).body());

        HttpResponse<String> response = post("/appointments",
                "_csrf=" + enc(csrf) + "&patientName=" + enc("Invalid Test")
                        + "&address=" + enc(TEST_ADDRESS) + "&contactNumber=not-a-phone"
                        + "&dentistId=1&treatmentId=2"
                        + "&appointmentDate=" + LocalDate.now().minusDays(3)
                        + "&appointmentTime=09%3A17", cookie);

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("already passed"), "past date should be named");
        assertTrue(response.body().contains("30 minutes"), "off-boundary time should be named");
        assertTrue(response.body().contains("contact number"), "bad phone should be named");
    }

    @Test
    @DisplayName("an administrator can add a treatment, and a duplicate code is refused")
    void adminCanManageTreatments() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");
        String csrf = csrfFrom(get("/admin/treatments", cookie).body());
        String code = "IT" + (System.currentTimeMillis() % 100000);

        HttpResponse<String> created = post("/admin/treatments",
                "_csrf=" + enc(csrf) + "&code=" + code + "&name=" + enc("Integration Test Treatment")
                        + "&family=CLEANING&baseCost=1234.00&durationMinutes=30", cookie);
        assertEquals(303, created.statusCode(), created.body());

        HttpResponse<String> duplicate = post("/admin/treatments",
                "_csrf=" + enc(csrf) + "&code=" + code + "&name=" + enc("Duplicate")
                        + "&family=CLEANING&baseCost=1.00&durationMinutes=30", cookie);
        assertEquals(422, duplicate.statusCode());
        assertTrue(duplicate.body().contains("already in use"), duplicate.body());
    }

    @Test
    @DisplayName("a dentist whose session ends at 16:00 cannot be booked at 16:30")
    void dentistSessionHoursAreEnforced() throws Exception {
        requireDatabase();
        String cookie = signIn("admin", "Admin@123");
        String csrf = csrfFrom(get("/appointments/new", cookie).body());

        HttpResponse<String> response = post("/appointments",
                "_csrf=" + enc(csrf) + "&patientName=" + enc("After Hours")
                        + "&address=" + enc(TEST_ADDRESS) + "&contactNumber=0771234567"
                        + "&dentistId=1&treatmentId=2&appointmentDate=" + FUTURE
                        + "&appointmentTime=16%3A30", cookie);

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("Nimal Perera"),
                "the message should name the dentist's hours: " + response.body());
    }
}
