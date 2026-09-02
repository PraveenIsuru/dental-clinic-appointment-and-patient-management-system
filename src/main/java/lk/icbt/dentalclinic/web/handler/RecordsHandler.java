package lk.icbt.dentalclinic.web.handler;

import com.sun.net.httpserver.HttpExchange;
import lk.icbt.dentalclinic.dao.DentistDao;
import lk.icbt.dentalclinic.dao.PatientDao;
import lk.icbt.dentalclinic.dao.TreatmentDao;
import lk.icbt.dentalclinic.dao.jdbc.TransactionManager;
import lk.icbt.dentalclinic.model.Dentist;
import lk.icbt.dentalclinic.model.Patient;
import lk.icbt.dentalclinic.model.Treatment;
import lk.icbt.dentalclinic.model.TreatmentFamily;
import lk.icbt.dentalclinic.security.Session;
import lk.icbt.dentalclinic.validation.Rules;
import lk.icbt.dentalclinic.validation.ValidationResult;
import lk.icbt.dentalclinic.web.Fragments;
import lk.icbt.dentalclinic.web.Handler;
import lk.icbt.dentalclinic.web.Requests;
import lk.icbt.dentalclinic.web.Responses;
import lk.icbt.dentalclinic.web.View;
import lk.icbt.dentalclinic.web.WebContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Map;

/**
 * Administrator management of patients, dentists and treatments.
 *
 * <p>One handler serves all three, parameterised by {@link Kind}. They share the same
 * shape — list at the top, create form below, validate, redirect on success — and three
 * near-identical classes would have been three places to fix the next bug in that shape.
 * Where they genuinely differ is the field set and the validation, which are the two
 * methods that switch on the kind.
 *
 * <p>All three routes sit under {@code /admin}, so
 * {@link lk.icbt.dentalclinic.security.AccessRules} has already refused anyone who is
 * not an administrator before this class runs.
 */
public final class RecordsHandler implements Handler {

    public enum Kind { PATIENTS, DENTISTS, TREATMENTS }

    private final Kind kind;
    private final PatientDao patientDao;
    private final DentistDao dentistDao;
    private final TreatmentDao treatmentDao;
    private final TransactionManager transactions;
    private final View view;

    public RecordsHandler(Kind kind, PatientDao patientDao, DentistDao dentistDao,
                          TreatmentDao treatmentDao, TransactionManager transactions,
                          View view) {
        this.kind = kind;
        this.patientDao = patientDao;
        this.dentistDao = dentistDao;
        this.treatmentDao = treatmentDao;
        this.transactions = transactions;
        this.view = view;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            create(exchange);
        } else {
            list(exchange, Map.of(), null, null);
        }
    }

    // ------------------------------------------------------------------ list

    private void list(HttpExchange exchange, Map<String, String> form,
                      ValidationResult errors, String alert) throws IOException {
        Map<String, Object> model = view.model(exchange);

        switch (kind) {
            case PATIENTS -> {
                String search = Requests.queryParam(exchange, "q").orElse("").trim();
                var patients = search.isEmpty()
                        ? patientDao.findAll()
                        : patientDao.searchByNameOrContact(search);
                model.put("rows", Fragments.patientTable(patients));
                model.put("count", patients.size());
                model.put("query", search);
            }
            case DENTISTS -> {
                var dentists = dentistDao.findAll();
                model.put("rows", Fragments.dentistTable(dentists));
                model.put("count", dentists.size());
            }
            case TREATMENTS -> {
                var treatments = treatmentDao.findAll();
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
        if (alert != null) {
            model.put("alert", alert);
        }

        view.render(exchange, errors != null && errors.hasErrors() ? 422 : 200,
                templateName(), model);
    }

    private String templateName() {
        return switch (kind) {
            case PATIENTS -> "records-patients";
            case DENTISTS -> "records-dentists";
            case TREATMENTS -> "records-treatments";
        };
    }

    // ------------------------------------------------------------------ create

    private void create(HttpExchange exchange) throws IOException {
        Session actor = WebContext.requireSession();
        Map<String, String> form = Requests.form(exchange);

        ValidationResult errors = validate(form);
        if (errors.hasErrors()) {
            list(exchange, form, errors, null);
            return;
        }

        String created = transactions.inTransactionAs(actor.getUserId(), () -> switch (kind) {
            case PATIENTS -> createPatient(form);
            case DENTISTS -> createDentist(form);
            case TREATMENTS -> createTreatment(form);
        });

        Responses.redirect(exchange, basePath() + "?created="
                + java.net.URLEncoder.encode(created, java.nio.charset.StandardCharsets.UTF_8));
    }

    private String basePath() {
        return switch (kind) {
            case PATIENTS -> "/admin/patients";
            case DENTISTS -> "/admin/dentists";
            case TREATMENTS -> "/admin/treatments";
        };
    }

    private String createPatient(Map<String, String> form) {
        Patient patient = Patient.builder()
                .patientNo(patientDao.nextPatientNo())
                .fullName(Requests.field(form, "fullName"))
                .address(Requests.field(form, "address"))
                .contactNumber(Rules.normalisePhone(Requests.field(form, "contactNumber")))
                .email(Requests.optionalField(form, "email"))
                .build();
        patientDao.create(patient);
        return patient.getPatientNo();
    }

    private String createDentist(Map<String, String> form) {
        Dentist dentist = Dentist.builder()
                .fullName(Requests.field(form, "fullName"))
                .specialization(Requests.field(form, "specialization"))
                .phone(Rules.normalisePhone(Requests.field(form, "contactNumber")))
                .email(Requests.optionalField(form, "email"))
                .sessionStart(AppointmentHandler.parseTime(form.get("sessionStart")))
                .sessionEnd(AppointmentHandler.parseTime(form.get("sessionEnd")))
                .active(true)
                .build();
        dentistDao.create(dentist);
        return dentist.getFullName();
    }

    private String createTreatment(Map<String, String> form) {
        Treatment treatment = new Treatment(0,
                Requests.field(form, "code").toUpperCase(),
                Requests.field(form, "name"),
                TreatmentFamily.of(Requests.field(form, "family")),
                Requests.optionalField(form, "description"),
                new BigDecimal(Requests.field(form, "baseCost")),
                Integer.parseInt(Requests.field(form, "durationMinutes")),
                true);
        treatmentDao.create(treatment);
        return treatment.getName();
    }

    // ------------------------------------------------------------------ validation

    private ValidationResult validate(Map<String, String> form) {
        ValidationResult result = ValidationResult.empty();

        switch (kind) {
            case PATIENTS -> {
                result.rejectIf(Rules.isBlank(Requests.field(form, "fullName")),
                        "fullName", "Enter the patient's name.");
                result.rejectIf(Rules.isBlank(Requests.field(form, "address")),
                        "address", "Enter the patient's address.");
                result.rejectIf(!Rules.isPhone(Requests.field(form, "contactNumber")),
                        "contactNumber", "Enter a contact number such as 0771234567.");
                String email = Requests.field(form, "email");
                result.rejectIf(Rules.isPresent(email) && !Rules.isEmail(email),
                        "email", "Enter a valid email address, or leave it blank.");
            }
            case DENTISTS -> {
                result.rejectIf(Rules.isBlank(Requests.field(form, "fullName")),
                        "fullName", "Enter the dentist's name.");
                result.rejectIf(Rules.isBlank(Requests.field(form, "specialization")),
                        "specialization", "Enter a specialisation.");
                result.rejectIf(!Rules.isPhone(Requests.field(form, "contactNumber")),
                        "contactNumber", "Enter a contact number such as 0771234567.");
                validateSession(form, result);
            }
            case TREATMENTS -> validateTreatment(form, result);
        }
        return result;
    }

    private void validateSession(Map<String, String> form, ValidationResult result) {
        LocalTime start = AppointmentHandler.parseTime(form.get("sessionStart"));
        LocalTime end = AppointmentHandler.parseTime(form.get("sessionEnd"));

        if (start == null) {
            result.reject("sessionStart", "Enter a session start time, such as 08:00.");
        }
        if (end == null) {
            result.reject("sessionEnd", "Enter a session end time, such as 16:00.");
        }
        if (start != null && end != null && !end.isAfter(start)) {
            // The database CHECK constraint says the same thing; catching it here
            // produces a readable message instead of a constraint violation.
            result.reject("sessionEnd", "The session must end after it starts.");
        }
    }

    private void validateTreatment(Map<String, String> form, ValidationResult result) {
        String code = Requests.field(form, "code").toUpperCase();
        if (Rules.isBlank(code)) {
            result.reject("code", "Enter a short code, such as CLEAN.");
        } else if (!code.matches("^[A-Z0-9_]{2,20}$")) {
            result.reject("code", "Use 2 to 20 capital letters, digits or underscores.");
        } else if (treatmentDao.existsByCode(code)) {
            result.reject("code", "That code is already in use.");
        }

        result.rejectIf(Rules.isBlank(Requests.field(form, "name")),
                "name", "Enter the treatment name.");

        try {
            TreatmentFamily.of(Requests.field(form, "family"));
        } catch (IllegalArgumentException e) {
            result.reject("family", "Choose a treatment family.");
        }

        try {
            BigDecimal cost = new BigDecimal(Requests.field(form, "baseCost"));
            result.rejectIf(cost.signum() < 0, "baseCost", "The cost cannot be negative.");
        } catch (NumberFormatException e) {
            result.reject("baseCost", "Enter the cost as a number, such as 5000.00.");
        }

        try {
            int minutes = Integer.parseInt(Requests.field(form, "durationMinutes"));
            result.rejectIf(minutes <= 0, "durationMinutes", "Enter a duration in minutes.");
        } catch (NumberFormatException e) {
            result.reject("durationMinutes", "Enter a duration in whole minutes, such as 30.");
        }
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
