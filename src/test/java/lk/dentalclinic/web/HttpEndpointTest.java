package lk.icbt.dentalclinic.web;

import lk.icbt.dentalclinic.Main;
import lk.icbt.dentalclinic.web.handler.HealthHandler;
import lk.icbt.dentalclinic.web.handler.StaticFileHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests that drive a real server over real HTTP, using only
 * {@link java.net.http.HttpClient} from the JDK - no MockMvc, no test framework
 * beyond the JUnit runner.
 *
 * <p>The server binds port 0, so the OS picks a free port and the suite never
 * collides with a development instance already running on 8080.
 */
class HttpEndpointTest {

    private static HttpServerBootstrap server;
    private static HttpClient client;

    @BeforeAll
    static void startServer() throws IOException {
        // A router built here rather than Main.buildRouter, so these transport-level
        // checks stay a unit test: the full application needs a database, and whether
        // static assets are served correctly has nothing to do with MySQL. The
        // database-backed routes are covered by LoginFlowIT.
        Router router = new Router()
                .get("/health", new HealthHandler(Instant.now(), Main.VERSION))
                .get("/**", new StaticFileHandler());

        server = HttpServerBootstrap.start(0, 4, router);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    private static HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(server.baseUrl() + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("GET /health reports UP with the application version")
    void healthEndpointReportsUp() throws Exception {
        HttpResponse<String> response = get("/health");

        assertEquals(200, response.statusCode());
        assertEquals("application/json; charset=utf-8",
                response.headers().firstValue("Content-Type").orElse(""));
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
        assertTrue(response.body().contains(Main.VERSION), response.body());
    }

    @Test
    @DisplayName("GET / serves the landing page")
    void rootServesIndex() throws Exception {
        HttpResponse<String> response = get("/");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Sunrise Dental Clinic"), response.body());
    }

    @Test
    @DisplayName("static assets are served with the right content type")
    void servesStylesheet() throws Exception {
        HttpResponse<String> response = get("/css/app.css");

        assertEquals(200, response.statusCode());
        assertEquals("text/css; charset=utf-8",
                response.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    @DisplayName("an unknown page returns 404, not a stack trace")
    void unknownPathReturns404() throws Exception {
        HttpResponse<String> response = get("/no-such-page.html");

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("404"), response.body());
        assertTrue(response.body().contains("no-such-page.html"), response.body());
    }

    @Test
    @DisplayName("hardening headers are present on every response")
    void securityHeadersPresent() throws Exception {
        HttpResponse<String> response = get("/health");

        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertEquals("DENY", response.headers().firstValue("X-Frame-Options").orElse(""));
    }

    @Test
    @DisplayName("POST to a GET-only route returns 405 and advertises Allow")
    void wrongMethodReturns405() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(server.baseUrl() + "/health"))
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
        assertTrue(response.headers().firstValue("Allow").orElse("").contains("GET"));
    }

    @Test
    @DisplayName("a path-traversal attempt cannot escape the static root")
    void pathTraversalIsRejected() throws Exception {
        // Sent over a raw socket: HttpClient would normalise ".." away before it reached
        // the server, so the guard would never actually be exercised.
        int status = rawRequestStatus("GET /../../pom.xml HTTP/1.1");

        assertNotEquals(200, status, "traversal must never succeed");
        assertTrue(status >= 400, "expected a 4xx rejection but got " + status);
    }

    private static int rawRequestStatus(String requestLine) throws IOException {
        try (Socket socket = new Socket("localhost", server.port())) {
            socket.setSoTimeout(5000);
            String raw = requestLine + "\r\nHost: localhost\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(raw.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
                String statusLine = reader.readLine();
                if (statusLine == null) {
                    return 400; // connection closed without a response is also a rejection
                }
                String[] parts = statusLine.split(" ");
                return parts.length > 1 ? Integer.parseInt(parts[1]) : 400;
            }
        }
    }
}
