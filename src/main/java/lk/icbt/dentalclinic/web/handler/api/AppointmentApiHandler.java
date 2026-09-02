package lk.icbt.dentalclinic.web.handler.api;

import com.sun.net.httpserver.HttpExchange;
import lk.icbt.dentalclinic.model.Appointment;
import lk.icbt.dentalclinic.model.RoleCode;
import lk.icbt.dentalclinic.security.Session;
import lk.icbt.dentalclinic.service.AppointmentService;
import lk.icbt.dentalclinic.service.BookingRequest;
import lk.icbt.dentalclinic.web.Handler;
import lk.icbt.dentalclinic.web.Router;
import lk.icbt.dentalclinic.web.WebContext;
import lk.icbt.dentalclinic.web.dto.ApiDto;
import lk.icbt.dentalclinic.web.json.Json;
import lk.icbt.dentalclinic.web.json.JsonObject;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Appointments over REST — the core of Task B requirement (i), *"a distributed
 * application with web services"*.
 *
 * <pre>
 *   GET  /api/v1/appointments?date=YYYY-MM-DD   the caller's appointments for a day
 *   GET  /api/v1/appointments/{no}              one appointment
 *   POST /api/v1/appointments                   book one
 *   POST /api/v1/appointments/{no}/cancel       cancel one
 * </pre>
 *
 * <p><strong>The same service objects as the web pages.</strong> These handlers call
 * {@link AppointmentService} exactly as {@code AppointmentHandler} does, so a booking made
 * through the API is validated identically, allocates its number from the same stored
 * procedure, and is refused by the same unique index. Two entry points and one set of
 * business rules — which is the point of having a service tier at all, and the reason the
 * API could be added in one milestone rather than being a second implementation.
 *
 * <p><strong>Authentication</strong> is the same session cookie the browser uses. A
 * separate API-key scheme would be more conventional for a public API, but it would be a
 * second authentication path to secure and to test, and this API's clients are the
 * clinic's own tools. Documented on {@code /api-docs} so it is a stated decision rather
 * than an omission.
 */
public final class AppointmentApiHandler {

    private final AppointmentService appointments;

    public AppointmentApiHandler(AppointmentService appointments) {
        this.appointments = appointments;
    }

    // ------------------------------------------------------- GET /api/v1/appointments

    public Handler list() {
        return ApiSupport.guard(exchange -> {
            Session actor = WebContext.requireSession();
            LocalDate date = ApiSupport.query(exchange, "date")
                    .map(LocalDate::parse)
                    .orElse(LocalDate.now());

            List<Appointment> found = appointments.listFor(actor, date);

            ApiSupport.okList(exchange, "appointments",
                    array -> array.addAll(found, ApiDto::appointmentSummary),
                    found.size());
        });
    }

    // -------------------------------------------------- GET /api/v1/appointments/{no}

    public Handler get() {
        return ApiSupport.guard(exchange -> {
            Session actor = WebContext.requireSession();
            String number = Router.pathParam(exchange, "no");

            // Throws AppointmentNotFoundException for both "no such number" and
            // "not yours", which ApiSupport maps to 404 either way (A6).
            Appointment appointment = appointments.findByNumber(number, actor);

            Json.JsonObjectBuilder body = Json.object();
            ApiDto.appointmentDetail(body, appointment);
            ApiSupport.ok(exchange, body);
        });
    }

    // ------------------------------------------------------ POST /api/v1/appointments

    public Handler create() {
        return ApiSupport.guard(exchange -> {
            Session actor = WebContext.requireSession();
            JsonObject body = ApiSupport.readBody(exchange);

            BookingRequest request = new BookingRequest(
                    body.getInt("patientId").orElse(null),
                    body.getString("patientName").orElse(null),
                    body.getString("address").orElse(null),
                    body.getString("contactNumber").orElse(null),
                    body.getString("email").orElse(null),
                    body.getInt("dentistId").orElse(null),
                    body.getInt("treatmentId").orElse(null),
                    body.getDate("date").orElse(null),
                    body.getTime("time").orElse(null),
                    body.getString("notes").orElse(null));

            // A patient books only for themselves — the id comes from the session, never
            // the body, exactly as in the web form. A tampered patientId is ignored
            // rather than rejected, because there is no legitimate reason to send one.
            if (actor.hasRole(RoleCode.PATIENT)) {
                Integer patientId = appointments.patientIdFor(actor).orElse(null);
                if (patientId == null) {
                    ApiSupport.error(exchange, 409, "no_patient_record",
                            "This login has no patient record.");
                    return;
                }
                request = request.forPatient(patientId);
            }

            Appointment booked = appointments.book(request, actor);

            Json.JsonObjectBuilder response = Json.object();
            ApiDto.appointmentDetail(response, booked);
            ApiSupport.created(exchange,
                    "/api/v1/appointments/" + booked.getAppointmentNo(), response);
        });
    }

    // ----------------------------------------- POST /api/v1/appointments/{no}/{action}

    public Handler action() {
        return ApiSupport.guard(exchange -> {
            Session actor = WebContext.requireSession();
            String number = Router.pathParam(exchange, "no");
            String action = Router.pathParam(exchange, "action");

            switch (action == null ? "" : action) {
                case "cancel" -> appointments.cancel(number, actor);
                case "confirm" -> appointments.confirm(number, actor);
                case "complete" -> appointments.complete(number, actor);
                default -> {
                    ApiSupport.error(exchange, 404, "not_found",
                            "No such action: " + action);
                    return;
                }
            }

            Json.JsonObjectBuilder body = Json.object();
            ApiDto.appointmentDetail(body, appointments.findByNumber(number, actor));
            ApiSupport.ok(exchange, body);
        });
    }

    // ------------------------------------ GET /api/v1/dentists/{id}/availability?date=

    public Handler availability() {
        return ApiSupport.guard(exchange -> {
            WebContext.requireSession();
            int dentistId = Integer.parseInt(Router.pathParam(exchange, "id"));
            LocalDate date = ApiSupport.query(exchange, "date")
                    .map(LocalDate::parse)
                    .orElse(LocalDate.now());

            var free = appointments.freeSlots(dentistId, date);

            Json.JsonObjectBuilder body = Json.object()
                    .put("dentistId", dentistId)
                    .put("date", date)
                    .put("freeCount", free.size());
            body.putArray("freeSlots",
                    array -> free.forEach(slot -> array.add(slot.toString())));
            ApiSupport.ok(exchange, body);
        });
    }
}
