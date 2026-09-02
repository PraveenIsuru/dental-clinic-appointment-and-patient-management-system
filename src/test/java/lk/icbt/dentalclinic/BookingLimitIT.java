package lk.icbt.dentalclinic;

import lk.icbt.dentalclinic.config.AppConfig;
import lk.icbt.dentalclinic.config.ServiceRegistry;
import lk.icbt.dentalclinic.web.HttpServerBootstrap;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The upcoming-booking limit, built test-first.
 *
 * <p>This class is the <strong>TDD evidence for Task C</strong>. It was written before
 * {@code AppointmentService} had any notion of a booking limit, run to observe it fail for
 * the right reason, and only then was the rule implemented. The captured output of each
 * phase is in {@code my-docs/task-c/tdd-evidence.md}.
 *
 * <p><strong>The rule.</strong> A patient may hold at most
 * {@code booking.max.upcoming} (3) appointments that have not yet happened. Real clinics
 * need this: without it one person can reserve a dentist's whole week from the
 * self-service portal and cancel the day before, and every slot they hold is a slot
 * somebody who needs it cannot book. Staff are exempt — a receptionist booking a course of
 * treatment for a patient is doing their job.
 */
class BookingLimitIT {

    private static final Pattern CSRF = Pattern.compile(
            "name=\"csrf-token\" content=\"([^\"]+)\"|name=\"_csrf\"\\s+value=\"([^\"]+)\"");

    private static HttpServerBootstrap server;
    private static ServiceRegistry registry;
    private static HttpClient client;
    private static boolean databaseAvailable;

    private static final LocalDate FUTURE = LocalDate.now().plusDays(90);
    private static final String TEST_ADDRESS = "LIMIT-IT Test Road";

    @BeforeAll
    static void startApplication() throws IOException {
        AppConfig config = AppConfig.load(Path.of("config", "application.properties"));
        try {
            registry = new ServiceRegistry(config);
            registry.connectionPool().verifyConnectivity();
            databaseAvailable = true;
        } catch (SQLException | RuntimeException e) {
            System.out.println("BookingLimitIT skipped: database unavailable -- " + e.getMessage());
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

    private static void clearTestData() {
        try (var connection = registry.connectionPool().borrow();
             var statement = connection.createStatement()) {
            // The patient under test is the seeded kasun.f, so remove only the rows this
            // class creates: everything on its own far-future date.
            statement.executeUpdate("DELETE FROM bills WHERE appointment_id IN "
                    + "(SELECT appointment_id FROM appointments "
                    + " WHERE appointment_date BETWEEN '" + FUTURE + "' AND '"
                    + FUTURE.plusDays(10) + "')");
            statement.executeUpdate("DELETE FROM appointments WHERE appointment_date "
                    + "BETWEEN '" + FUTURE + "' AND '" + FUTURE.plusDays(10) + "'");
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

    /** The patient books for themselves; the day offset keeps each booking distinct. */
    private static HttpResponse<String> patientBooks(String cookie, int dayOffset)
            throws Exception {
        String csrf = csrfFrom(get("/appointments/new", cookie).body());
        return post("/appointments",
                "_csrf=" + enc(csrf)
                        + "&dentistId=1&treatmentId=2"
                        + "&appointmentDate=" + FUTURE.plusDays(dayOffset)
                        + "&appointmentTime=09%3A00", cookie);
    }

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("a patient may hold three upcoming appointments but not a fourth")
    void patientUpcomingBookingsAreCapped() throws Exception {
        requireDatabase();
        String patient = signIn("kasun.f", "Patient@123");

        // The seed gives this patient one upcoming appointment already, so clear the
        // slate by cancelling everything upcoming before counting.
        cancelAllUpcoming(patient);

        for (int day = 0; day < 3; day++) {
            HttpResponse<String> response = patientBooks(patient, day);
            assertEquals(303, response.statusCode(),
                    "booking " + (day + 1) + " of 3 should be accepted: " + response.body());
        }

        HttpResponse<String> fourth = patientBooks(patient, 3);

        assertEquals(409, fourth.statusCode(),
                "the fourth upcoming booking should be refused");
        assertTrue(fourth.body().contains("3 upcoming appointments"),
                "the message should say what the limit is: " + fourth.body());
    }

    @Test
    @DisplayName("cancelling one frees the allowance again")
    void cancellingRestoresTheAllowance() throws Exception {
        requireDatabase();
        String patient = signIn("kasun.f", "Patient@123");
        cancelAllUpcoming(patient);

        String first = null;
        for (int day = 4; day < 7; day++) {
            HttpResponse<String> response = patientBooks(patient, day);
            assertEquals(303, response.statusCode(), response.body());
            if (first == null) {
                first = numberFrom(response);
            }
        }
        assertEquals(409, patientBooks(patient, 7).statusCode(), "at the limit");

        // Cancel one, and the allowance returns.
        String csrf = csrfFrom(get("/appointments/" + first, patient).body());
        assertEquals(303,
                post("/appointments/" + first + "/cancel", "_csrf=" + enc(csrf), patient)
                        .statusCode());

        assertEquals(303, patientBooks(patient, 7).statusCode(),
                "a cancelled appointment must not keep counting against the limit");
    }

    @Test
    @DisplayName("staff are not capped — a receptionist books a course of treatment")
    void staffAreExempt() throws Exception {
        requireDatabase();
        String admin = signIn("admin", "Admin@123");

        // Five bookings for one new patient, well past the patient limit.
        String patientNo = null;
        for (int day = 0; day < 5; day++) {
            String csrf = csrfFrom(get("/appointments/new", admin).body());
            HttpResponse<String> response = post("/appointments",
                    "_csrf=" + enc(csrf)
                            + "&patientName=" + enc("Course Of Treatment")
                            + "&address=" + enc(TEST_ADDRESS)
                            + "&contactNumber=0771234567"
                            + "&dentistId=2&treatmentId=2"
                            + "&appointmentDate=" + FUTURE.plusDays(day)
                            + "&appointmentTime=13%3A00", admin);

            assertEquals(303, response.statusCode(),
                    "staff booking " + (day + 1) + " should be accepted: " + response.body());
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String numberFrom(HttpResponse<String> response) {
        Matcher matcher = Pattern.compile("APT-\\d{4}-\\d{4}")
                .matcher(response.headers().firstValue("Location").orElse(""));
        assertTrue(matcher.find());
        return matcher.group();
    }

    /** Cancels every upcoming appointment the patient holds, so each test starts level. */
    private static void cancelAllUpcoming(String patientCookie) throws Exception {
        Matcher matcher = Pattern.compile("APT-\\d{4}-\\d{4}")
                .matcher(get("/appointments", patientCookie).body());

        java.util.Set<String> numbers = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            numbers.add(matcher.group());
        }
        for (String number : numbers) {
            HttpResponse<String> detail = get("/appointments/" + number, patientCookie);
            if (detail.statusCode() != 200 || !detail.body().contains("/cancel")) {
                continue;   // already terminal, or outside the 24-hour window
            }
            post("/appointments/" + number + "/cancel",
                    "_csrf=" + enc(csrfFrom(detail.body())), patientCookie);
        }
    }
}
