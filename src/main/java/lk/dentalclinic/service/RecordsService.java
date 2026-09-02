package lk.dentalclinic.service;

import lk.dentalclinic.dao.DentistDao;
import lk.dentalclinic.dao.PatientDao;
import lk.dentalclinic.dao.TreatmentDao;
import lk.dentalclinic.dao.jdbc.TransactionManager;
import lk.dentalclinic.model.Dentist;
import lk.dentalclinic.model.Patient;
import lk.dentalclinic.model.Treatment;
import lk.dentalclinic.model.TreatmentFamily;
import lk.dentalclinic.security.Session;
import lk.dentalclinic.validation.Rules;
import lk.dentalclinic.validation.ValidationResult;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * Administrator management of patients, dentists and treatments.
 *
 * <p><strong>Extracted in M5, because the architecture test caught its absence.</strong>
 * {@code RecordsHandler} was doing this work itself: validating, building entities and
 * opening transactions, all in the presentation tier. It compiled and the screens worked,
 * so nothing complained — until {@code ArchitectureTest} reported
 * {@code web.handler.RecordsHandler imports dao.jdbc.TransactionManager} and made the
 * erosion visible.
 *
 * <p>That is the whole argument for the test, and it belongs in the report: the tier
 * boundary was not broken by anyone deciding to break it, but by a handler quietly growing
 * business logic one method at a time. A claim of three tiers that nothing enforces
 * becomes false without anybody noticing.
 *
 * <p>The handler now binds the form and renders; this class owns the rules and the
 * transaction.
 */
public final class RecordsService {

    private static final Logger LOG = Logger.getLogger(RecordsService.class.getName());

    private final PatientDao patientDao;
    private final DentistDao dentistDao;
    private final TreatmentDao treatmentDao;
    private final TransactionManager transactions;

    public RecordsService(PatientDao patientDao, DentistDao dentistDao,
                          TreatmentDao treatmentDao, TransactionManager transactions) {
        this.patientDao = patientDao;
        this.dentistDao = dentistDao;
        this.treatmentDao = treatmentDao;
        this.transactions = transactions;
    }

    /** What was created, or why it was refused. */
    public record Outcome(ValidationResult validation, String createdLabel) {

        public boolean isSuccess() {
            return validation.isValid();
        }

        static Outcome rejected(ValidationResult validation) {
            return new Outcome(validation, null);
        }

        static Outcome created(String label) {
            return new Outcome(ValidationResult.empty(), label);
        }
    }

    // --------------------------------------------------------------- requests

    public record PatientForm(String fullName, String address, String contactNumber,
                              String email) {
    }

    public record DentistForm(String fullName, String specialization, String phone,
                              String email, LocalTime sessionStart, LocalTime sessionEnd) {
    }

    public record TreatmentForm(String code, String name, String family, String description,
                                String baseCost, String durationMinutes) {
    }

    // --------------------------------------------------------------- patients

    public Outcome addPatient(PatientForm form, Session actor) {
        ValidationResult result = ValidationResult.empty();
        result.rejectIf(Rules.isBlank(form.fullName()),
                "fullName", "Enter the patient's name.");
        result.rejectIf(Rules.isBlank(form.address()),
                "address", "Enter the patient's address.");
        result.rejectIf(!Rules.isPhone(form.contactNumber()),
                "contactNumber", "Enter a contact number such as 0771234567.");
        result.rejectIf(Rules.isPresent(form.email()) && !Rules.isEmail(form.email()),
                "email", "Enter a valid email address, or leave it blank.");

        if (result.hasErrors()) {
            return Outcome.rejected(result);
        }

        String patientNo = transactions.inTransactionAs(actor.getUserId(), () -> {
            Patient patient = Patient.builder()
                    .patientNo(patientDao.nextPatientNo())
                    .fullName(form.fullName().trim())
                    .address(form.address().trim())
                    .contactNumber(Rules.normalisePhone(form.contactNumber()))
                    .email(blankToNull(form.email()))
                    .build();
            patientDao.create(patient);
            return patient.getPatientNo();
        });

        LOG.info(() -> "Registered patient " + patientNo + " by " + actor.getUsername());
        return Outcome.created(patientNo);
    }

    public List<Patient> listPatients(String search) {
        return Rules.isBlank(search)
                ? patientDao.findAll()
                : patientDao.searchByNameOrContact(search);
    }

    // --------------------------------------------------------------- dentists

    public Outcome addDentist(DentistForm form, Session actor) {
        ValidationResult result = ValidationResult.empty();
        result.rejectIf(Rules.isBlank(form.fullName()),
                "fullName", "Enter the dentist's name.");
        result.rejectIf(Rules.isBlank(form.specialization()),
                "specialization", "Enter a specialisation.");
        result.rejectIf(!Rules.isPhone(form.phone()),
                "contactNumber", "Enter a contact number such as 0771234567.");

        if (form.sessionStart() == null) {
            result.reject("sessionStart", "Enter a session start time, such as 08:00.");
        }
        if (form.sessionEnd() == null) {
            result.reject("sessionEnd", "Enter a session end time, such as 16:00.");
        }
        if (form.sessionStart() != null && form.sessionEnd() != null
                && !form.sessionEnd().isAfter(form.sessionStart())) {
            // The database CHECK constraint says the same; catching it here produces a
            // readable message rather than a constraint violation.
            result.reject("sessionEnd", "The session must end after it starts.");
        }

        if (result.hasErrors()) {
            return Outcome.rejected(result);
        }

        transactions.inTransactionAs(actor.getUserId(), () -> dentistDao.create(
                Dentist.builder()
                        .fullName(form.fullName().trim())
                        .specialization(form.specialization().trim())
                        .phone(Rules.normalisePhone(form.phone()))
                        .email(blankToNull(form.email()))
                        .sessionStart(form.sessionStart())
                        .sessionEnd(form.sessionEnd())
                        .active(true)
                        .build()));

        LOG.info(() -> "Added dentist " + form.fullName() + " by " + actor.getUsername());
        return Outcome.created(form.fullName().trim());
    }

    public List<Dentist> listDentists() {
        return dentistDao.findAll();
    }

    // ------------------------------------------------------------- treatments

    public Outcome addTreatment(TreatmentForm form, Session actor) {
        ValidationResult result = ValidationResult.empty();
        String code = form.code() == null ? "" : form.code().trim().toUpperCase();

        if (Rules.isBlank(code)) {
            result.reject("code", "Enter a short code, such as CLEAN.");
        } else if (!code.matches("^[A-Z0-9_]{2,20}$")) {
            result.reject("code", "Use 2 to 20 capital letters, digits or underscores.");
        } else if (treatmentDao.existsByCode(code)) {
            result.reject("code", "That code is already in use.");
        }

        result.rejectIf(Rules.isBlank(form.name()), "name", "Enter the treatment name.");

        TreatmentFamily family = null;
        try {
            family = TreatmentFamily.of(form.family());
        } catch (IllegalArgumentException | NullPointerException e) {
            result.reject("family", "Choose a treatment family.");
        }

        BigDecimal cost = null;
        try {
            cost = new BigDecimal(form.baseCost().trim());
            result.rejectIf(cost.signum() < 0, "baseCost", "The cost cannot be negative.");
        } catch (RuntimeException e) {
            result.reject("baseCost", "Enter the cost as a number, such as 5000.00.");
        }

        int minutes = 0;
        try {
            minutes = Integer.parseInt(form.durationMinutes().trim());
            result.rejectIf(minutes <= 0, "durationMinutes", "Enter a duration in minutes.");
        } catch (RuntimeException e) {
            result.reject("durationMinutes", "Enter a duration in whole minutes, such as 30.");
        }

        if (result.hasErrors()) {
            return Outcome.rejected(result);
        }

        Treatment treatment = new Treatment(0, code, form.name().trim(), family,
                blankToNull(form.description()), cost, minutes, true);
        transactions.inTransactionAs(actor.getUserId(), () -> treatmentDao.create(treatment));

        LOG.info(() -> "Added treatment " + code + " by " + actor.getUsername());
        return Outcome.created(treatment.getName());
    }

    public List<Treatment> listTreatments() {
        return treatmentDao.findAll();
    }

    private static String blankToNull(String value) {
        return Rules.isBlank(value) ? null : value.trim();
    }
}
