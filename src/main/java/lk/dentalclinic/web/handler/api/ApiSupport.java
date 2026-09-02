package lk.dentalclinic.web.handler.api;

import com.sun.net.httpserver.HttpExchange;
import lk.dentalclinic.dao.BusinessRuleViolationException;
import lk.dentalclinic.dao.DataAccessException;
import lk.dentalclinic.service.AppointmentNotFoundException;
import lk.dentalclinic.service.BillNotFoundException;
import lk.dentalclinic.service.BillingNotAllowedException;
import lk.dentalclinic.service.BookingNotAllowedException;
import lk.dentalclinic.service.SlotUnavailableException;
import lk.dentalclinic.service.ValidationException;
import lk.dentalclinic.web.Handler;
import lk.dentalclinic.web.Requests;
import lk.dentalclinic.web.Responses;
import lk.dentalclinic.web.json.Json;
import lk.dentalclinic.web.json.JsonException;
import lk.dentalclinic.web.json.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared plumbing for the REST API: reading a JSON body, writing a JSON response, and
 * translating an exception into a status code.
 *
 * <p>The exception mapping is the important part, and it lives here rather than being
 * repeated in each handler — this is the hand-written equivalent of Spring's
 * {@code @RestControllerAdvice}. One table, so a new endpoint cannot invent its own
 * meaning for "not found".
 *
 * <table>
 *   <caption>Exception to status</caption>
 *   <tr><th>Exception</th><th>Status</th><th>Why</th></tr>
 *   <tr><td>{@code JsonException}</td><td>400</td><td>the body is malformed</td></tr>
 *   <tr><td>{@code ValidationException}</td><td>422</td><td>well-formed, but a field is wrong</td></tr>
 *   <tr><td>{@code *NotFoundException}</td><td>404</td><td>including "not yours" — see A6</td></tr>
 *   <tr><td>{@code SlotUnavailableException}</td><td>409</td><td>a conflict with existing state</td></tr>
 *   <tr><td>{@code BillingNotAllowedException}</td><td>409</td><td>as above</td></tr>
 *   <tr><td>{@code BusinessRuleViolationException}</td><td>409</td><td>a database trigger refused it</td></tr>
 *   <tr><td>anything else</td><td>500</td><td>logged in full, reported without detail</td></tr>
 * </table>
 *
 * <p><strong>A 500 never carries the exception message.</strong> Internal detail —
 * table names, SQL fragments, file paths — is exactly what an attacker probing an API
 * hopes to collect. The client gets a generic message and a reference; the log gets
 * everything.
 */
public final class ApiSupport {

    private static final Logger LOG = Logger.getLogger("api");

    /** Same cap as the form parser: an unbounded body is a free denial of service. */
    private static final int MAX_BODY_BYTES = 256 * 1024;

    private ApiSupport() {
    }

    // ------------------------------------------------------------------ reading

    /** Reads and parses a JSON request body. */
    public static JsonObject readBody(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType != null && !contentType.startsWith("application/json")) {
            throw new JsonException("Content-Type must be application/json, was: " + contentType);
        }
        try (InputStream in = exchange.getRequestBody()) {
            byte[] body = in.readNBytes(MAX_BODY_BYTES);
            return Json.parseObject(new String(body, StandardCharsets.UTF_8));
        }
    }

    public static java.util.Optional<String> query(HttpExchange exchange, String name) {
        return Requests.queryParam(exchange, name);
    }

    // ------------------------------------------------------------------ writing

    public static void ok(HttpExchange exchange, Json.JsonObjectBuilder body) throws IOException {
        Responses.json(exchange, 200, body.toJson());
    }

    /** A list response, wrapped in an envelope carrying the count. */
    public static void okList(HttpExchange exchange, String name,
                              java.util.function.Consumer<Json.JsonArrayBuilder> build,
                              int count) throws IOException {
        Json.JsonObjectBuilder body = Json.object().put("count", count);
        body.putArray(name, build);
        Responses.json(exchange, 200, body.toJson());
    }

    /**
     * 201 with a {@code Location} header naming the new resource.
     *
     * <p>Required by REST and genuinely useful: a client that has just posted a booking
     * learns its appointment number from the header without parsing the body.
     */
    public static void created(HttpExchange exchange, String location,
                               Json.JsonObjectBuilder body) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        Responses.json(exchange, 201, body.toJson());
    }

    public static void noContent(HttpExchange exchange) throws IOException {
        Responses.send(exchange, 204, "application/json; charset=utf-8", new byte[0]);
    }

    // ------------------------------------------------------------------ errors

    /**
     * Wraps a handler so every exception becomes a JSON error rather than an HTML page.
     *
     * <p>An API client sending {@code Accept: application/json} and receiving a 500 page
     * of markup has no way to report what went wrong. This is why the API cannot simply
     * reuse the site's error handling.
     */
    public static Handler guard(Handler handler) {
        return exchange -> {
            try {
                handler.handle(exchange);

            } catch (JsonException e) {
                error(exchange, 400, "bad_request", e.getMessage());

            } catch (ValidationException e) {
                Json.JsonObjectBuilder body = errorBody("validation_failed",
                        "The request contains invalid fields");
                body.putObject("fields", fields ->
                        e.result().errors().forEach(fields::put));
                Responses.json(exchange, 422, body.toJson());

            } catch (AppointmentNotFoundException | BillNotFoundException e) {
                // Also covers "exists but not yours" — see assumption A6.
                error(exchange, 404, "not_found", e.getMessage());

            } catch (SlotUnavailableException e) {
                Json.JsonObjectBuilder body = errorBody("slot_unavailable", e.getMessage());
                body.putArray("suggestions", suggestions ->
                        e.suggestions().forEach(time -> suggestions.add(time.toString())));
                Responses.json(exchange, 409, body.toJson());

            } catch (BillingNotAllowedException | BookingNotAllowedException
                     | BusinessRuleViolationException e) {
                error(exchange, 409, "conflict", e.getMessage());

            } catch (IllegalStateException e) {
                error(exchange, 409, "conflict", e.getMessage());

            } catch (DataAccessException e) {
                LOG.log(Level.SEVERE, "Data access failure on "
                        + exchange.getRequestURI(), e);
                error(exchange, 500, "internal_error",
                        "The request could not be completed. The problem has been logged.");

            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Unhandled failure on " + exchange.getRequestURI(), e);
                error(exchange, 500, "internal_error",
                        "The request could not be completed. The problem has been logged.");
            }
        };
    }

    public static void error(HttpExchange exchange, int status, String code, String message)
            throws IOException {
        Responses.json(exchange, status, errorBody(code, message).toJson());
    }

    /** One envelope shape for every error, so a client can handle them uniformly. */
    private static Json.JsonObjectBuilder errorBody(String code, String message) {
        return Json.object()
                .put("error", code)
                .put("message", message == null ? "" : message);
    }
}
