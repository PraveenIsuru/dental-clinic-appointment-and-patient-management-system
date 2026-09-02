package lk.icbt.dentalclinic;

import lk.icbt.dentalclinic.config.AppConfig;
import lk.icbt.dentalclinic.config.ServiceRegistry;
import lk.icbt.dentalclinic.web.HttpServerBootstrap;
import lk.icbt.dentalclinic.web.json.Json;
import lk.icbt.dentalclinic.web.json.JsonObject;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The REST API end to end — evidence for Task B requirement (i), *"a distributed
 * application with web services"*.
 *
 * <p>Every request here is JSON over HTTP with no browser involved, which is the claim
 * the requirement makes. The suite deliberately drives the <em>whole</em> business flow
 * through the API — book, complete, bill, pay, report — because an API that can only read
 * is not a service layer, it is a feed.
 *
 * <p>It also asserts the things that separate a REST API from a set of endpoints: the
 * status codes, the {@code Location} header on create, the JSON error envelope, and the
 * fact that a failure never returns an HTML page to a client that asked for JSON.
 */
class ApiFlowIT {

    private static HttpServerBootstrap server;
    private static ServiceRegistry registry;
    private static HttpClient client;
    private static boolean databaseAvailable;

    private static final LocalDate FUTURE = LocalDate.now().plusDays(75);
    private static final String TEST_ADDRESS = "API-IT Test Road";

    private static String adminCookie;
    private static String adminCsrf;

    @BeforeAll
    static void startApplication() throws Exception {
        AppConfig config = AppConfig.load(Path.of("config", "application.properties"));
        try {
            registry = new ServiceRegistry(config);
            registry.connectionPool().verifyConnectivity();
            databaseAvailable = true;
        } catch (SQLException | RuntimeException e) {
            System.out.println("ApiFlowIT skipped: database unavailable -- " + e.getMessage());
            databaseAvailable = false;
            return;
        }
        server = HttpServerBootstrap.start(0, 4, Main.buildRouter(registry, Instant.now()));
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        clearTestData();

        adminCookie = signIn("admin", "Admin@123");
        adminCsrf = json(get("/api/v1/session", adminCookie)).requireString("csrfToken");
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
            statement.executeUpdate("DELETE FROM bills WHERE appointment_id IN "
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
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> postJson(String path, String body, String cookie,
                                                 String csrf) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server.baseUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        if (csrf != null) {
            request.header("X-CSRF-Token", csrf);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String signIn(String username, String password) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(server.baseUrl() + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=" + username
                        + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(303, response.statusCode());
        return response.headers().allValues("Set-Cookie").stream()
                .filter(h -> h.startsWith("SESSIONID=") && !h.contains("Max-Age=0"))
                .map(h -> h.substring(0, h.indexOf(';')))
                .findFirst().orElseThrow(() -> new AssertionError("no session cookie"));
    }

    /** Parses a response body, failing with the body text when it is not JSON. */
    private static JsonObject json(HttpResponse<String> response) {
        try {
            return Json.parseObject(response.body());
        } catch (RuntimeException e) {
            throw new AssertionError("Response was not JSON (" + response.statusCode() + "): "
                    + response.body(), e);
        }
    }

    private static String bookViaApi(String time, String patientName) throws Exception {
        String body = """
                {"patientName": "%s",
                 "address": "%s",
                 "contactNumber": "0771234567",
                 "dentistId": 1, "treatmentId": 2,
                 "date": "%s", "time": "%s"}
                """.formatted(patientName, TEST_ADDRESS, FUTURE, time);

        HttpResponse<String> response =
                postJson("/api/v1/appointments", body, adminCookie, adminCsrf);
        assertEquals(201, response.statusCode(), response.body());
        return json(response).requireString("appointmentNo");
    }

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("GET /api/v1/session returns the caller and a CSRF token")
    void sessionEndpoint() throws Exception {
        requireDatabase();

        JsonObject session = json(get("/api/v1/session", adminCookie));

        assertEquals("admin", session.requireString("username"));
        assertEquals("ADMIN", session.requireString("role"));
        assertFalse(session.requireString("csrfToken").isBlank());
        assertEquals("X-CSRF-Token", session.requireString("csrfHeader"));
    }

    @Test
    @DisplayName("an unauthenticated API call returns 401 JSON, not an HTML redirect")
    void unauthenticatedGetsJson() throws Exception {
        requireDatabase();

        HttpResponse<String> response = get("/api/v1/appointments", null);

        assertEquals(401, response.statusCode(),
                "a redirect to a login page is useless to an API client");
        assertEquals("unauthenticated", json(response).requireString("error"));
        assertTrue(response.headers().firstValue("Content-Type").orElse("")
                .startsWith("application/json"));
    }

    @Test
    @DisplayName("a POST without the CSRF header is refused as JSON")
    void csrfIsRequiredOnWrites() throws Exception {
        requireDatabase();

        HttpResponse<String> response =
                postJson("/api/v1/appointments", "{}", adminCookie, null);

        assertEquals(403, response.statusCode());
        assertEquals("csrf_failed", json(response).requireString("error"));
    }

    @Test
    @DisplayName("reference data gives a client what it needs to book")
    void referenceData() throws Exception {
        requireDatabase();

        JsonObject dentists = json(get("/api/v1/dentists", adminCookie));
        assertTrue(dentists.getInt("count").orElseThrow() > 0);

        JsonObject treatments = json(get("/api/v1/treatments", adminCookie));
        assertTrue(treatments.getInt("count").orElseThrow() >= 7);

        JsonObject availability =
                json(get("/api/v1/dentists/1/availability?date=" + FUTURE, adminCookie));
        assertTrue(availability.getInt("freeCount").orElseThrow() > 0);
        assertFalse(availability.getArray("freeSlots").isEmpty());
    }

    @Test
    @DisplayName("booking returns 201 with a Location header and the allocated number")
    void bookingReturnsCreated() throws Exception {
        requireDatabase();

        String body = """
                {"patientName": "Api Created Patient",
                 "address": "%s",
                 "contactNumber": "0771234567",
                 "dentistId": 1, "treatmentId": 2,
                 "date": "%s", "time": "09:00"}
                """.formatted(TEST_ADDRESS, FUTURE);

        HttpResponse<String> response =
                postJson("/api/v1/appointments", body, adminCookie, adminCsrf);

        assertEquals(201, response.statusCode(), response.body());
        String location = response.headers().firstValue("Location").orElseThrow();
        assertTrue(location.startsWith("/api/v1/appointments/APT-"), location);
        assertTrue(json(response).requireString("appointmentNo").matches("APT-\\d{4}-\\d{4}"));
    }

    @Test
    @DisplayName("a double booking is 409 with free-slot suggestions in the body")
    void doubleBookingReturnsConflictWithSuggestions() throws Exception {
        requireDatabase();
        bookViaApi("09:30", "First Api Patient");

        String body = """
                {"patientName": "Second Api Patient",
                 "address": "%s",
                 "contactNumber": "0779876543",
                 "dentistId": 1, "treatmentId": 2,
                 "date": "%s", "time": "09:30"}
                """.formatted(TEST_ADDRESS, FUTURE);

        HttpResponse<String> response =
                postJson("/api/v1/appointments", body, adminCookie, adminCsrf);

        assertEquals(409, response.statusCode());
        JsonObject error = json(response);
        assertEquals("slot_unavailable", error.requireString("error"));
        assertFalse(error.getArray("suggestions").isEmpty(),
                "the refusal should offer alternatives, as the web form does");
    }

    @Test
    @DisplayName("a validation failure is 422 and names every bad field at once")
    void validationFailureListsFields() throws Exception {
        requireDatabase();

        String body = """
                {"patientName": "", "address": "",
                 "contactNumber": "not-a-phone",
                 "dentistId": 1, "treatmentId": 2,
                 "date": "2020-01-01", "time": "09:17"}
                """;

        HttpResponse<String> response =
                postJson("/api/v1/appointments", body, adminCookie, adminCsrf);

        assertEquals(422, response.statusCode());
        JsonObject error = json(response);
        assertEquals("validation_failed", error.requireString("error"));

        JsonObject fields = error.getObject("fields").orElseThrow();
        assertTrue(fields.fieldNames().size() >= 3,
                "all failures should be reported together: " + fields.fieldNames());
        assertTrue(fields.has("patientName"));
        assertTrue(fields.has("contactNumber"));
    }

    @Test
    @DisplayName("a malformed body is 400, not 500")
    void malformedJsonIsBadRequest() throws Exception {
        requireDatabase();

        HttpResponse<String> response =
                postJson("/api/v1/appointments", "{not json", adminCookie, adminCsrf);

        assertEquals(400, response.statusCode());
        assertEquals("bad_request", json(response).requireString("error"));
    }

    @Test
    @DisplayName("an unknown appointment number is 404 with a JSON error")
    void unknownResourceIsNotFound() throws Exception {
        requireDatabase();

        HttpResponse<String> response =
                get("/api/v1/appointments/APT-1999-9999", adminCookie);

        assertEquals(404, response.statusCode());
        assertEquals("not_found", json(response).requireString("error"));
    }

    @Test
    @DisplayName("A6 over the API: another patient's appointment is 404, never 403")
    void otherPatientsAppointmentIsNotFound() throws Exception {
        requireDatabase();
        String appointmentNo = bookViaApi("10:00", "Not The Api Caller");

        String patientCookie = signIn("kasun.f", "Patient@123");
        HttpResponse<String> response = get("/api/v1/appointments/" + appointmentNo, patientCookie);

        assertEquals(404, response.statusCode(),
                "403 would confirm the number is real, and numbers are sequential");
        assertNotEquals(403, response.statusCode());
        assertFalse(response.body().contains("Not The Api Caller"));
    }

    @Test
    @DisplayName("no response anywhere leaks a password hash")
    void noPasswordHashInAnyResponse() throws Exception {
        requireDatabase();
        String appointmentNo = bookViaApi("10:30", "Hash Check Patient");

        for (String path : new String[]{
                "/api/v1/session", "/api/v1/patients", "/api/v1/dentists",
                "/api/v1/appointments", "/api/v1/appointments/" + appointmentNo}) {
            String body = get(path, adminCookie).body();
            assertFalse(body.contains("pbkdf2"), path + " leaked a password hash");
            assertFalse(body.toLowerCase().contains("passwordhash"), path + " leaked a field name");
        }
    }

    @Test
    @DisplayName("the whole business flow runs through the API: book, complete, bill, pay")
    void endToEndBusinessFlowOverRest() throws Exception {
        requireDatabase();

        // 1. Book.
        String appointmentNo = bookViaApi("11:00", "Full Flow Patient");

        // 2. Complete, which makes it billable.
        HttpResponse<String> completed = postJson(
                "/api/v1/appointments/" + appointmentNo + "/complete", "{}",
                adminCookie, adminCsrf);
        assertEquals(200, completed.statusCode(), completed.body());
        assertTrue(json(completed).getBoolean("billable").orElseThrow());

        // 3. Bill it, with a 10% discount.
        HttpResponse<String> billed = postJson("/api/v1/bills",
                "{\"appointmentNo\":\"" + appointmentNo + "\",\"quantity\":1,"
                        + "\"discountPercent\":10}", adminCookie, adminCsrf);
        assertEquals(201, billed.statusCode(), billed.body());

        JsonObject bill = json(billed);
        String billNo = bill.requireString("billNo");
        JsonObject charges = bill.getObject("charges").orElseThrow();

        // 2500 consultation + 5000 cleaning = 7500, less 10% = 6750.
        assertEquals(0, new java.math.BigDecimal("7500.00")
                .compareTo(charges.getNumber("subtotal").orElseThrow()));
        assertEquals(0, new java.math.BigDecimal("750.00")
                .compareTo(charges.getNumber("discount").orElseThrow()));
        assertEquals(0, new java.math.BigDecimal("6750.00")
                .compareTo(charges.getNumber("total").orElseThrow()));
        assertFalse(bill.getArray("lineItems").isEmpty(), "the bill must be itemised");

        // 4. Record payment.
        HttpResponse<String> paid =
                postJson("/api/v1/bills/" + billNo + "/pay", "{}", adminCookie, adminCsrf);
        assertEquals(200, paid.statusCode(), paid.body());
        assertEquals("PAID", json(paid).requireString("status"));
    }

    @Test
    @DisplayName("the report endpoints name the stored routines that compute them")
    void reportsComeFromStoredRoutines() throws Exception {
        requireDatabase();

        JsonObject revenue =
                json(get("/api/v1/reports/revenue?date=" + LocalDate.now(), adminCookie));
        assertEquals("sp_daily_revenue_report", revenue.requireString("source"));

        JsonObject workload = json(get("/api/v1/reports/workload", adminCookie));
        assertEquals("vw_dentist_workload", workload.requireString("source"));
        assertTrue(workload.getInt("count").orElseThrow() > 0);

        JsonObject daily =
                json(get("/api/v1/reports/daily?date=" + LocalDate.now(), adminCookie));
        assertTrue(daily.getObject("appointments").isPresent());
        assertTrue(daily.getObject("chairTime").isPresent());
        assertTrue(daily.getObject("revenue").isPresent());
    }

    @Test
    @DisplayName("a patient sees only their own record from the patients endpoint")
    void patientEndpointIsScoped() throws Exception {
        requireDatabase();
        String patientCookie = signIn("kasun.f", "Patient@123");

        JsonObject response = json(get("/api/v1/patients", patientCookie));

        assertEquals(1, response.getInt("count").orElseThrow(),
                "a patient must not be able to list the clinic's patients");
    }

    @Test
    @DisplayName("the API reference and Postman collection are reachable without signing in")
    void documentationIsPublic() throws Exception {
        requireDatabase();

        HttpResponse<String> docs = get("/api-docs.html", null);
        assertEquals(200, docs.statusCode());

        // The extensionless alias the documentation cites must work too.
        assertEquals(303, get("/api-docs", null).statusCode());
        assertTrue(docs.body().contains("/api/v1/appointments"));

        HttpResponse<String> collection = get("/postman-collection.json", null);
        assertEquals(200, collection.statusCode());
        // It must be valid JSON, or a marker importing it into Postman gets an error.
        JsonObject parsed = Json.parseObject(collection.body());
        assertTrue(parsed.getObject("info").orElseThrow()
                .requireString("name").contains("Sunrise"));
    }
}
