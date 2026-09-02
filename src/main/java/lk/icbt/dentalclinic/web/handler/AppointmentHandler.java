package lk.icbt.dentalclinic.web.handler;

import com.sun.net.httpserver.HttpExchange;
import lk.icbt.dentalclinic.dao.DentistDao;
import lk.icbt.dentalclinic.dao.PatientDao;
import lk.icbt.dentalclinic.dao.TreatmentDao;
import lk.icbt.dentalclinic.model.Appointment;
import lk.icbt.dentalclinic.model.RoleCode;
import lk.icbt.dentalclinic.security.Session;
import lk.icbt.dentalclinic.service.AppointmentService;
import lk.icbt.dentalclinic.service.BookingRequest;
import lk.icbt.dentalclinic.service.SlotUnavailableException;
import lk.icbt.dentalclinic.service.ValidationException;
import lk.icbt.dentalclinic.web.Fragments;
import lk.icbt.dentalclinic.web.Handler;
import lk.icbt.dentalclinic.web.Requests;
import lk.icbt.dentalclinic.web.Responses;
import lk.icbt.dentalclinic.web.View;
import lk.icbt.dentalclinic.web.WebContext;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * The appointment list and the booking form — brief requirement 2.
 *
 * <p>{@code GET /appointments} shows what the session is entitled to see;
 * {@code GET /appointments/new} shows the form; {@code POST /appointments} books.
 *
 * <p>A patient booking for themselves never supplies a patient id: it is taken from the
 * session, so a patient cannot book in somebody else's name by editing a hidden field.
 */
public final class AppointmentHandler implements Handler {

    private final AppointmentService appointments;
    private final PatientDao patientDao;
    private final DentistDao dentistDao;
    private final TreatmentDao treatmentDao;
    private final View view;

    public AppointmentHandler(AppointmentService appointments, PatientDao patientDao,
                              DentistDao dentistDao, TreatmentDao treatmentDao, View view) {
        this.appointments = appointments;
        this.patientDao = patientDao;
        this.dentistDao = dentistDao;
        this.treatmentDao = treatmentDao;
        this.view = view;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            book(exchange);
        } else {
            list(exchange);
        }
    }

    // ------------------------------------------------------------------ list

    private void list(HttpExchange exchange) throws IOException {
        Session actor = WebContext.requireSession();
        LocalDate date = parseDate(Requests.queryParam(exchange, "date").orElse(null))
                .orElse(LocalDate.now());

        List<Appointment> found = appointments.listFor(actor, date);

        Map<String, Object> model = view.model(exchange);
        model.put("date", date);
        model.put("dateIso", date.toString());
        model.put("isPatient", actor.hasRole(RoleCode.PATIENT));
        model.put("showsWholeHistory", actor.hasRole(RoleCode.PATIENT));
        model.put("count", found.size());
        model.put("rows", Fragments.appointmentTable(found, !actor.hasRole(RoleCode.PATIENT)));
        model.put("canBook", !actor.hasRole(RoleCode.DENTIST));
        view.render(exchange, "appointments", model);
    }

    // ------------------------------------------------------------- booking form

    /** {@code GET /appointments/new} — also the target of the availability grid's links. */
    public Handler newForm() {
        return exchange -> {
            Session actor = WebContext.requireSession();
            if (actor.hasRole(RoleCode.DENTIST)) {
                // A dentist does not take bookings; the desk does.
                Responses.redirect(exchange, "/appointments");
                return;
            }
            Map<String, Object> model = view.model(exchange);
            Integer dentistId = parseInt(Requests.queryParam(exchange, "dentistId").orElse(null));
            LocalDate date = parseDate(Requests.queryParam(exchange, "date").orElse(null))
                    .orElse(LocalDate.now());
            LocalTime time = parseTime(Requests.queryParam(exchange, "time").orElse(null));

            populateForm(model, actor, dentistId, null, date, time);
            view.render(exchange, "appointment-new", model);
        };
    }

    private void populateForm(Map<String, Object> model, Session actor, Integer dentistId,
                              Integer treatmentId, LocalDate date, LocalTime time) {
        model.put("dentistOptions", Fragments.dentistOptions(dentistDao.findActive(), dentistId));
        model.put("treatmentOptions",
                Fragments.treatmentOptions(treatmentDao.findActive(), treatmentId));
        model.put("dateIso", date == null ? LocalDate.now().toString() : date.toString());
        model.put("timeValue", Fragments.time(time));
        model.put("minDate", LocalDate.now().toString());
        model.put("maxDate", LocalDate.now().plusMonths(6).toString());

        boolean bookingForSelf = actor.hasRole(RoleCode.PATIENT);
        model.put("bookingForSelf", bookingForSelf);
        if (bookingForSelf) {
            patientDao.findByUserId(actor.getUserId()).ifPresent(patient -> {
                model.put("patientName", patient.getFullName());
                model.put("patientNo", patient.getPatientNo());
                model.put("contactNumber", patient.getContactNumber());
                model.put("address", patient.getAddress());
            });
        }

        if (dentistId != null && date != null) {
            model.put("freeSlots",
                    Fragments.timeOptions(appointments.freeSlots(dentistId, date), time));
            model.put("hasSlotList", true);
        }
    }

    // ------------------------------------------------------------------ create

    private void book(HttpExchange exchange) throws IOException {
        Session actor = WebContext.requireSession();
        Map<String, String> form = Requests.form(exchange);

        BookingRequest request = new BookingRequest(
                null,
                Requests.field(form, "patientName"),
                Requests.field(form, "address"),
                Requests.field(form, "contactNumber"),
                Requests.optionalField(form, "email"),
                parseInt(form.get("dentistId")),
                parseInt(form.get("treatmentId")),
                parseDate(form.get("appointmentDate")).orElse(null),
                parseTime(form.get("appointmentTime")),
                Requests.optionalField(form, "notes"));

        // A patient books only for themselves; the id comes from the session, never
        // from the form, so a tampered field cannot book in another patient's name.
        if (actor.hasRole(RoleCode.PATIENT)) {
            var patient = patientDao.findByUserId(actor.getUserId());
            if (patient.isEmpty()) {
                Responses.html(exchange, 500, lk.icbt.dentalclinic.web.Pages.serverError());
                return;
            }
            request = request.forPatient(patient.get().getId());
        } else if (Requests.optionalField(form, "patientId") != null) {
            request = request.forPatient(parseInt(form.get("patientId")));
        }

        try {
            Appointment booked = appointments.book(request, actor);
            Responses.redirect(exchange,
                    "/appointments/" + booked.getAppointmentNo() + "?booked=1");

        } catch (ValidationException e) {
            redisplay(exchange, actor, request, form, 422, null,
                    e.result().errors());

        } catch (SlotUnavailableException e) {
            String message = e.getMessage() + Fragments.slotSuggestions(e.suggestions());
            redisplay(exchange, actor, request, form, 409, message, Map.of());
        }
    }

    private void redisplay(HttpExchange exchange, Session actor, BookingRequest request,
                           Map<String, String> form, int status, String alert,
                           Map<String, String> fieldErrors) throws IOException {
        Map<String, Object> model = view.model(exchange);
        populateForm(model, actor, request.dentistId(), request.treatmentId(),
                request.appointmentDate(), request.appointmentTime());

        // Keep what the user typed; making them start again over one bad field is how
        // a booking desk ends up keeping a paper diary instead.
        Fragments.echo(model, form, "patientName", "address", "contactNumber", "email", "notes");

        fieldErrors.forEach((field, message) -> model.put("error_" + field, message));
        model.put("hasErrors", !fieldErrors.isEmpty());
        if (alert != null) {
            model.put("alert", alert);
        }
        view.render(exchange, status, "appointment-new", model);
    }

    // ------------------------------------------------------------------ parsing

    static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static java.util.Optional<LocalDate> parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(LocalDate.parse(raw.trim()));
        } catch (DateTimeParseException e) {
            // A malformed date is a validation failure, not a crash: return empty and
            // let the validator report "Choose a date".
            return java.util.Optional.empty();
        }
    }

    static LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String value = raw.trim();
            return LocalTime.parse(value.length() == 5 ? value + ":00" : value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
