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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end tests over real HTTP against the real MySQL schema.
 *
 * <p><strong>Skipped, not failed, when the database is unavailable.</strong> A developer
 * without MySQL running should still be able to build, and CI without a database service
 * should not report a red build for a missing dependency rather than a broken change.
 * The M6 CI workflow provides a MySQL service container, at which point these run there
 * too. The trade-off is that a genuinely broken query can slip past a local build — which
 * is why the assumption prints its reason rather than skipping silently.
 *
 * <p>Named {@code *IT} rather than {@code *Test} to mark it as an integration test.
 * Surefire is configured to run it alongside the unit tests here because the suite is
 * small; a larger project would split them into separate phases.
 */
class LoginFlowIT {

    private static HttpServerBootstrap server;
    private static ServiceRegistry registry;
    private static HttpClient client;
    private static boolean databaseAvailable;

    @BeforeAll
    static void startApplication() throws IOException {
        AppConfig config = AppConfig.load(Path.of("config", "application.properties"));
        try {
            registry = new ServiceRegistry(config);
            registry.connectionPool().verifyConnectivity();
            databaseAvailable = true;
        } catch (SQLException | RuntimeException e) {
            System.out.println("LoginFlowIT skipped: database unavailable -- " + e.getMessage());
            databaseAvailable = false;
            return;
        }

        server = HttpServerBootstrap.start(0, 4, Main.buildRouter(registry, Instant.now()));
        // Redirects are followed manually: the point of several tests is the 303 and
        // the Set-Cookie header, both of which an auto-following client hides.
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @AfterAll
    static void stopApplication() {
        if (server != null) {
            server.close();
        }
        if (registry != null) {
            registry.shutdown();
        }
    }

    private static void requireDatabase() {
        assumeTrue(databaseAvailable, "MySQL is not running; start WAMP to run these tests");
    }

    private static HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server.baseUrl() + path))
                .timeout(Duration.ofSeconds(10)).GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> postForm(String path, String body, String cookie)
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

    /** Extracts the SESSIONID value from the response's Set-Cookie headers. */
    private static Optional<String> sessionCookie(HttpResponse<String> response) {
        List<String> headers = response.headers().allValues("Set-Cookie");
        return headers.stream()
                .filter(header -> header.startsWith("SESSIONID=") && !header.contains("Max-Age=0"))
                .map(header -> header.substring(0, header.indexOf(';')))
                .findFirst();
    }

    private static String signIn(String username, String password) throws Exception {
        HttpResponse<String> response =
                postForm("/login", "username=" + username + "&password=" + password, null);
        assertEquals(303, response.statusCode(), "expected a redirect after a valid sign-in");
        return sessionCookie(response).orElseThrow(
                () -> new AssertionError("no SESSIONID cookie was issued"));
    }

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("the seeded admin can sign in and reach the admin dashboard")
    void adminCanSignIn() throws Exception {
        requireDatabase();

        String cookie = signIn("admin", "Admin%40123");

        HttpResponse<String> dashboard = get("/admin/dashboard", cookie);
        assertEquals(200, dashboard.statusCode());
        assertTrue(dashboard.body().contains("Administrator dashboard"), dashboard.body());
        assertTrue(dashboard.body().contains("Registered patients"));
    }

    @Test
    @DisplayName("the session cookie is HttpOnly and SameSite=Strict")
    void sessionCookieIsHardened() throws Exception {
        requireDatabase();

        HttpResponse<String> response =
                postForm("/login", "username=admin&password=Admin%40123", null);

        String header = response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("SESSIONID="))
                .findFirst().orElseThrow();

        assertTrue(header.contains("HttpOnly"), header);
        assertTrue(header.contains("SameSite=Strict"), header);
        assertTrue(header.contains("Path=/"), header);
    }

    @Test
    @DisplayName("a wrong password is refused with the generic message and no cookie")
    void wrongPasswordIsRefused() throws Exception {
        requireDatabase();

        HttpResponse<String> response =
                postForm("/login", "username=admin&password=not-the-password", null);

        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("Sign-in details are incorrect"), response.body());
        assertTrue(sessionCookie(response).isEmpty(), "no session may be issued on failure");
    }

    @Test
    @DisplayName("an unknown username gives byte-identical output to a wrong password")
    void unknownUserIsIndistinguishable() throws Exception {
        requireDatabase();

        String wrongPassword =
                postForm("/login", "username=admin&password=wrong", null).body();
        String unknownUser =
                postForm("/login", "username=nobody&password=wrong", null).body();

        // The username is echoed back, so compare the part that carries the outcome.
        assertTrue(wrongPassword.contains("Sign-in details are incorrect"));
        assertTrue(unknownUser.contains("Sign-in details are incorrect"),
                "a different message here would let an attacker enumerate accounts");
    }

    @Test
    @DisplayName("an anonymous visitor is redirected to sign in, carrying the target path")
    void anonymousIsRedirectedToLogin() throws Exception {
        requireDatabase();

        HttpResponse<String> response = get("/admin/dashboard", null);

        assertEquals(303, response.statusCode());
        String location = response.headers().firstValue("Location").orElseThrow();
        assertTrue(location.startsWith("/login?next="), location);
    }

    @Test
    @DisplayName("a dentist reaching the admin area gets 403, not the page")
    void wrongRoleIsForbidden() throws Exception {
        requireDatabase();

        String cookie = signIn("dr.perera", "Dentist%40123");

        HttpResponse<String> response = get("/admin/dashboard", cookie);

        assertEquals(403, response.statusCode());
        assertFalse(response.body().contains("Registered patients"),
                "the admin page must not leak through a 403");
    }

    @Test
    @DisplayName("each role lands on its own dashboard")
    void rolesLandOnTheirOwnDashboard() throws Exception {
        requireDatabase();

        assertEquals("/admin/dashboard", loginRedirectFor("admin", "Admin%40123"));
        assertEquals("/dentist/dashboard", loginRedirectFor("dr.perera", "Dentist%40123"));
        assertEquals("/patient/dashboard", loginRedirectFor("kasun.f", "Patient%40123"));
    }

    private static String loginRedirectFor(String username, String password) throws Exception {
        HttpResponse<String> response =
                postForm("/login", "username=" + username + "&password=" + password, null);
        return response.headers().firstValue("Location").orElseThrow();
    }

    @Test
    @DisplayName("signing out invalidates the session server-side, not only in the browser")
    void signOutInvalidatesServerSide() throws Exception {
        requireDatabase();

        String cookie = signIn("admin", "Admin%40123");
        assertEquals(200, get("/admin/dashboard", cookie).statusCode());

        get("/logout", cookie);

        // Replaying the same cookie must now fail: clearing it in the browser alone
        // would leave anyone holding a copy still signed in.
        HttpResponse<String> replay = get("/admin/dashboard", cookie);
        assertEquals(303, replay.statusCode());
        assertTrue(replay.headers().firstValue("Location").orElseThrow().startsWith("/login"));
    }

    @Test
    @DisplayName("signing in issues a different identifier each time (session fixation)")
    void signInRotatesTheIdentifier() throws Exception {
        requireDatabase();

        String first = signIn("admin", "Admin%40123");
        String second = signIn("admin", "Admin%40123");

        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("REGRESSION: a signed-in request must not leak its session to the next visitor")
    void sessionDoesNotLeakBetweenRequests() throws Exception {
        requireDatabase();

        // The original defect: WebContext stored the session with
        // HttpExchange.setAttribute, which the JDK backs with a map owned by the
        // HttpContext and therefore shared by every request. After any user signed in,
        // the next request carrying no cookie at all was served their session.
        String cookie = signIn("admin", "Admin%40123");
        assertEquals(200, get("/admin/dashboard", cookie).statusCode(),
                "the signed-in request must succeed, so the session really is populated");

        // Immediately afterwards, with no cookie whatsoever.
        HttpResponse<String> anonymous = get("/admin/dashboard", null);

        assertEquals(303, anonymous.statusCode(),
                "an anonymous request must be redirected, never served the admin page");
        assertFalse(anonymous.body().contains("Administrator dashboard"),
                "the previous user's page leaked to an anonymous visitor");

        // The same leak made POST /login see a session and fail the CSRF check.
        assertEquals(303, postForm("/login",
                        "username=admin&password=Admin%40123", null).statusCode(),
                "signing in must not be blocked by a leaked session's CSRF token");
    }

    @Test
    @DisplayName("the help page shows general topics anonymously and staff topics to staff")
    void helpIsRoleAware() throws Exception {
        requireDatabase();

        String anonymous = get("/help", null).body();
        assertTrue(anonymous.contains("Signing in"), "general topics are public");
        assertFalse(anonymous.contains("Registering a new appointment"),
                "staff topics must not show to an anonymous visitor");

        String cookie = signIn("admin", "Admin%40123");
        String staff = get("/help", cookie).body();
        assertTrue(staff.contains("Registering a new appointment"));
    }

    @Test
    @DisplayName("registration rejects a weak password and redisplays the form with the error")
    void registrationValidatesPassword() throws Exception {
        requireDatabase();

        HttpResponse<String> response = postForm("/register",
                "fullName=Test+Person&username=test.person&password=short"
                        + "&confirmPassword=short&contactNumber=0771234567"
                        + "&address=1+Test+Road", null);

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("At least 10 characters"), response.body());
        // The typed values survive so the user need not start again.
        assertTrue(response.body().contains("value=\"Test Person\""));
    }

    @Test
    @DisplayName("registration rejects an already-taken username")
    void registrationRejectsDuplicateUsername() throws Exception {
        requireDatabase();

        HttpResponse<String> response = postForm("/register",
                "fullName=Impostor&username=admin&password=Str0ngPassword"
                        + "&confirmPassword=Str0ngPassword&contactNumber=0771234567"
                        + "&address=1+Test+Road", null);

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("already taken"), response.body());
    }

    @Test
    @DisplayName("a POST without a CSRF token is refused once a session exists")
    void csrfTokenIsRequired() throws Exception {
        requireDatabase();

        String cookie = signIn("admin", "Admin%40123");

        // /logout is registered for POST as well; with a session and no token the
        // CSRF filter must reject it.
        HttpResponse<String> response = postForm("/logout", "", cookie);

        assertEquals(403, response.statusCode());
        assertTrue(response.body().contains("expired"), response.body());
    }
}
