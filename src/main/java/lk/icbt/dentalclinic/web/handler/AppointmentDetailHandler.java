package lk.icbt.dentalclinic.web.handler;

import com.sun.net.httpserver.HttpExchange;
import lk.icbt.dentalclinic.model.Appointment;
import lk.icbt.dentalclinic.model.RoleCode;
import lk.icbt.dentalclinic.security.Session;
import lk.icbt.dentalclinic.service.AppointmentNotFoundException;
import lk.icbt.dentalclinic.service.AppointmentService;
import lk.icbt.dentalclinic.service.SlotUnavailableException;
import lk.icbt.dentalclinic.service.ValidationException;
import lk.icbt.dentalclinic.web.Fragments;
import lk.icbt.dentalclinic.web.Handler;
import lk.icbt.dentalclinic.web.Pages;
import lk.icbt.dentalclinic.web.Requests;
import lk.icbt.dentalclinic.web.Responses;
import lk.icbt.dentalclinic.web.Router;
import lk.icbt.dentalclinic.web.View;
import lk.icbt.dentalclinic.web.WebContext;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * One appointment: the detail view of brief requirement 3, and the actions on it.
 *
 * <p>{@code GET /appointments/{no}} shows the record;
 * {@code POST /appointments/{no}/{action}} confirms, completes, cancels or reschedules.
 *
 * <p>Every path funnels through {@link AppointmentService#findByNumber}, which applies
 * the access policy. A patient asking for another patient's number therefore gets a
 * plain 404 here — see assumption A6 and {@link AppointmentNotFoundException}.
 */
public final class AppointmentDetailHandler implements Handler {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy");

    private final AppointmentService appointments;
    private final View view;

    public AppointmentDetailHandler(AppointmentService appointments, View view) {
        this.appointments = appointments;
        this.view = view;
    }

    // ------------------------------------------------------------------ search

    /**
     * {@code GET /appointments/search?q=APT-2026-0001} — requirement 3.
     *
     * <p>On a hit it redirects to the detail page rather than rendering it, so the
     * result has its own shareable address and a refresh does not repeat the search.
     */
    public Handler search() {
        return exchange -> {
            Session actor = WebContext.requireSession();
            String query = Requests.queryParam(exchange, "q").orElse("").trim();

            Map<String, Object> model = view.model(exchange);
            model.put("query", query);

            if (query.isEmpty()) {
                view.render(exchange, "appointment-search", model);
                return;
            }
            try {
                Appointment found = appointments.findByNumber(query, actor);
                Responses.redirect(exchange, "/appointments/"
                        + URLEncoder.encode(found.getAppointmentNo(), StandardCharsets.UTF_8));
            } catch (AppointmentNotFoundException e) {
                model.put("notFound", true);
                view.render(exchange, 404, "appointment-search", model);
            }
        };
    }

    // ------------------------------------------------------------------ detail

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Session actor = WebContext.requireSession();
        String number = Router.pathParam(exchange, "no");

        try {
            render(exchange, appointments.findByNumber(number, actor), actor, null, null);
        } catch (AppointmentNotFoundException e) {
            Responses.html(exchange, 404, Pages.notFound());
        }
    }

    private void render(HttpExchange exchange, Appointment appointment, Session actor,
                        String alert, String error) throws IOException {
        Map<String, Object> model = view.model(exchange);

        model.put("appointmentNo", appointment.getAppointmentNo());
        model.put("dateLong", DATE.format(appointment.getAppointmentDate()));
        model.put("dateIso", appointment.getAppointmentDate().toString());
        model.put("timeValue", Fragments.time(appointment.getAppointmentTime()));
        model.put("statusBadge", Fragments.statusBadge(appointment.getStatus()));
        model.put("status", appointment.getStatus().name());
        model.put("notes", appointment.getNotes());

        if (appointment.getPatient() != null) {
            model.put("patientName", appointment.getPatient().getFullName());
            model.put("patientNo", appointment.getPatient().getPatientNo());
            model.put("address", appointment.getPatient().getAddress());
            model.put("contactNumber", appointment.getPatient().getContactNumber());
        }
        if (appointment.getDentist() != null) {
            model.put("dentistName", appointment.getDentist().getFullName());
            model.put("specialization", appointment.getDentist().getSpecialization());
        }
        if (appointment.getTreatment() != null) {
            model.put("treatmentName", appointment.getTreatment().getName());
            model.put("treatmentCost", appointment.getTreatment().getBaseCost().toPlainString());
            model.put("durationMinutes", appointment.getTreatment().getDurationMinutes());
        }

        boolean staff = !actor.hasRole(RoleCode.PATIENT);
        boolean open = !appointment.getStatus().isTerminal();
        model.put("canConfirm", staff && open
                && appointment.getStatus().canTransitionTo(
                        lk.icbt.dentalclinic.model.AppointmentStatus.CONFIRMED));
        model.put("canComplete", staff && open);
        model.put("canCancel", open && appointment.canBeCancelledBy(actor.getRole()));
        model.put("canReschedule", open && appointment.canBeCancelledBy(actor.getRole())
                && !actor.hasRole(RoleCode.DENTIST));
        model.put("minDate", LocalDate.now().toString());

        if (Requests.queryParam(exchange, "booked").isPresent()) {
            model.put("justBooked", true);
        }
        // Carried across the POST/redirect/GET so the confirmation survives the redirect.
        Requests.queryParam(exchange, "done").ifPresent(message -> model.put("alert", message));
        if (alert != null) {
            model.put("alert", alert);
        }
        if (error != null) {
            model.put("error", error);
        }

        view.render(exchange, "appointment-detail", model);
    }

    // ------------------------------------------------------------------ actions

    /** {@code POST /appointments/{no}/{action}} — confirm, complete, cancel, reschedule. */
    public Handler action() {
        return exchange -> {
            Session actor = WebContext.requireSession();
            String number = Router.pathParam(exchange, "no");
            String action = Router.pathParam(exchange, "action");

            try {
                Appointment appointment = appointments.findByNumber(number, actor);

                switch (action == null ? "" : action) {
                    case "confirm" -> {
                        appointments.confirm(number, actor);
                        redirectBack(exchange, number, "Appointment confirmed.");
                    }
                    case "complete" -> {
                        appointments.complete(number, actor);
                        redirectBack(exchange, number, "Treatment marked complete.");
                    }
                    case "cancel" -> {
                        appointments.cancel(number, actor);
                        redirectBack(exchange, number, "Appointment cancelled.");
                    }
                    case "reschedule" -> reschedule(exchange, actor, number);
                    default -> Responses.html(exchange, 404, Pages.notFound());
                }
            } catch (AppointmentNotFoundException e) {
                Responses.html(exchange, 404, Pages.notFound());
            } catch (IllegalStateException e) {
                // A refusal the user can act on — wrong status, or outside the 24-hour
                // window. Shown on the page rather than as a 500.
                Appointment appointment = appointments.findByNumber(number, actor);
                render(exchange, appointment, actor, null, e.getMessage());
            }
        };
    }

    private void reschedule(HttpExchange exchange, Session actor, String number)
            throws IOException {
        Map<String, String> form = Requests.form(exchange);
        LocalDate date = AppointmentHandler.parseDate(form.get("appointmentDate")).orElse(null);
        LocalTime time = AppointmentHandler.parseTime(form.get("appointmentTime"));

        try {
            appointments.reschedule(number, date, time, actor);
            redirectBack(exchange, number, "Appointment moved.");
        } catch (ValidationException e) {
            render(exchange, appointments.findByNumber(number, actor), actor, null,
                    e.result().firstError().orElse("That change is not valid."));
        } catch (SlotUnavailableException e) {
            render(exchange, appointments.findByNumber(number, actor), actor, null,
                    e.getMessage() + Fragments.slotSuggestions(e.suggestions()));
        }
    }

    /** POST/redirect/GET, so a refresh cannot repeat the action. */
    private static void redirectBack(HttpExchange exchange, String number, String message)
            throws IOException {
        Responses.redirect(exchange, "/appointments/"
                + URLEncoder.encode(number, StandardCharsets.UTF_8)
                + "?done=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }
}
