package lk.dentalclinic.web.handler;

import com.sun.net.httpserver.HttpExchange;
import lk.dentalclinic.model.TreatmentFamily;
import lk.dentalclinic.security.Session;
import lk.dentalclinic.service.RecordsService;
import lk.dentalclinic.validation.ValidationResult;
import lk.dentalclinic.web.Fragments;
import lk.dentalclinic.web.Handler;
import lk.dentalclinic.web.Requests;
import lk.dentalclinic.web.Responses;
import lk.dentalclinic.web.View;
import lk.dentalclinic.web.WebContext;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Administrator management of patients, dentists and treatments.
 *
 * <p>One handler serves all three, parameterised by {@link Kind}. They share the same
 * shape — list at the top, create form below, validate, redirect on success — and three
 * near-identical classes would have been three places to fix the next bug in that shape.
 *
 * <p><strong>Rewritten in M5.</strong> This class previously validated the forms, built
 * the entities and opened the transactions itself, which put business logic and a JDBC
 * dependency in the presentation tier. {@code ArchitectureTest} caught it, and the work
 * moved to {@link RecordsService}. What is left here is what a handler should do: read the
 * request, call one service method, render the result.
 *
 * <p>All three routes sit under {@code /admin}, so
 * {@link lk.dentalclinic.security.AccessRules} has already refused anyone who is not
 * an administrator before this class runs.
 */
public final class RecordsHandler implements Handler {

    public enum Kind { PATIENTS, DENTISTS, TREATMENTS }

    private final Kind kind;
    private final RecordsService records;
    private final View view;

    public RecordsHandler(Kind kind, RecordsService records, View view) {
        this.kind = kind;
        this.records = records;
        this.view = view;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            create(exchange);
        } else {
            render(exchange, Map.of(), null, 200);
        }
    }

    // ------------------------------------------------------------------ render

    private void render(HttpExchange exchange, Map<String, String> form,
                        ValidationResult errors, int status) throws IOException {
        Map<String, Object> model = view.model(exchange);

        switch (kind) {
            case PATIENTS -> {
                String search = Requests.queryParam(exchange, "q").orElse("").trim();
                var patients = records.listPatients(search);
                model.put("rows", Fragments.patientTable(patients));
                model.put("count", patients.size());
                model.put("query", search);
            }
            case DENTISTS -> {
                var dentists = records.listDentists();
                model.put("rows", Fragments.dentistTable(dentists));
                model.put("count", dentists.size());
            }
            case TREATMENTS -> {
                var treatments = records.listTreatments();
                model.put("rows", Fragments.treatmentTable(treatments));
                model.put("count", treatments.size());
                model.put("familyOptions", familyOptions(form.get("family")));
            }
        }

        if (!form.isEmpty()) {
            Fragments.echo(model, form, "fullName", "address", "contactNumber", "email",
                    "specialization", "sessionStart", "sessionEnd",
                    "code", "name", "description", "baseCost", "durationMinutes");
        }
        if (errors != null) {
            errors.errors().forEach((field, message) -> model.put("error_" + field, message));
            model.put("hasErrors", errors.hasErrors());
        }
        Requests.queryParam(exchange, "created")
                .ifPresent(created -> model.put("alert", created + " added."));

        view.render(exchange, status, templateName(), model);
    }

    private String templateName() {
        return switch (kind) {
            case PATIENTS -> "records-patients";
            case DENTISTS -> "records-dentists";
            case TREATMENTS -> "records-treatments";
        };
    }

    private String basePath() {
        return switch (kind) {
            case PATIENTS -> "/admin/patients";
            case DENTISTS -> "/admin/dentists";
            case TREATMENTS -> "/admin/treatments";
        };
    }

    // ------------------------------------------------------------------ create

    private void create(HttpExchange exchange) throws IOException {
        Session actor = WebContext.requireSession();
        Map<String, String> form = Requests.form(exchange);

        RecordsService.Outcome outcome = switch (kind) {
            case PATIENTS -> records.addPatient(new RecordsService.PatientForm(
                    Requests.field(form, "fullName"),
                    Requests.field(form, "address"),
                    Requests.field(form, "contactNumber"),
                    Requests.field(form, "email")), actor);

            case DENTISTS -> records.addDentist(new RecordsService.DentistForm(
                    Requests.field(form, "fullName"),
                    Requests.field(form, "specialization"),
                    Requests.field(form, "contactNumber"),
                    Requests.field(form, "email"),
                    AppointmentHandler.parseTime(form.get("sessionStart")),
                    AppointmentHandler.parseTime(form.get("sessionEnd"))), actor);

            case TREATMENTS -> records.addTreatment(new RecordsService.TreatmentForm(
                    Requests.field(form, "code"),
                    Requests.field(form, "name"),
                    Requests.field(form, "family"),
                    Requests.field(form, "description"),
                    Requests.field(form, "baseCost"),
                    Requests.field(form, "durationMinutes")), actor);
        };

        if (!outcome.isSuccess()) {
            render(exchange, form, outcome.validation(), 422);
            return;
        }

        // POST/redirect/GET, so a refresh cannot create a second record.
        Responses.redirect(exchange, basePath() + "?created="
                + URLEncoder.encode(outcome.createdLabel(), StandardCharsets.UTF_8));
    }

    private static String familyOptions(String selected) {
        StringBuilder html = new StringBuilder("<option value=\"\">Choose a family…</option>\n");
        for (TreatmentFamily family : TreatmentFamily.values()) {
            html.append("<option value=\"").append(family.name()).append('"')
                    .append(family.name().equals(selected) ? " selected" : "").append('>')
                    .append(family.name().replace('_', ' ').toLowerCase())
                    .append("</option>\n");
        }
        return html.toString();
    }
}
