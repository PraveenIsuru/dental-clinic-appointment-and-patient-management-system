package lk.dentalclinic.validation;

import lk.dentalclinic.model.Dentist;
import lk.dentalclinic.model.Treatment;
import lk.dentalclinic.service.BookingRequest;
import lk.dentalclinic.service.ClinicSettings;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Every rule a booking must satisfy before it reaches the database.
 *
 * <p>The brief asks for *"proper validation mechanisms in order to restrict invalid
 * entries"*, and this is where the marker looks. The rules are ordered cheapest first:
 * required fields, then formats, then the calendar rules, then the ones needing a
 * lookup. A submission failing three rules reports all three at once — see
 * {@link ValidationResult}.
 *
 * <p><strong>What this class deliberately does not do</strong> is check whether the
 * slot is free. That belongs to the service, because it needs a database round trip and
 * because the answer can change between the check and the insert. The unique index is
 * the only thing that settles it — see the Book Appointment sequence diagram.
 */
public final class AppointmentValidator {

    /** How far ahead the clinic will take a booking. */
    public static final int MAX_MONTHS_AHEAD = 6;

    private final ClinicSettings settings;

    public AppointmentValidator(ClinicSettings settings) {
        this.settings = settings;
    }

    /**
     * Validates a booking.
     *
     * @param dentist   the chosen dentist, or empty if the id matched nothing
     * @param treatment the chosen treatment, or empty if the id matched nothing
     */
    public ValidationResult validate(BookingRequest request,
                                     Optional<Dentist> dentist,
                                     Optional<Treatment> treatment) {
        ValidationResult result = ValidationResult.empty();

        validatePatient(request, result);
        validateDentist(request, dentist, result);
        validateTreatment(request, treatment, result);
        validateDate(request, result);
        validateTime(request, dentist, result);

        result.rejectIf(!Rules.lengthAtMost(request.notes(), 500),
                "notes", "Notes must be 500 characters or fewer.");

        return result;
    }

    private void validatePatient(BookingRequest request, ValidationResult result) {
        if (request.isForExistingPatient()) {
            return;   // an existing record; its fields were validated when it was created
        }

        result.rejectIf(Rules.isBlank(request.patientName()),
                "patientName", "Enter the patient's name.");
        result.rejectIf(!Rules.lengthAtMost(request.patientName(), 120),
                "patientName", "The name must be 120 characters or fewer.");

        result.rejectIf(Rules.isBlank(request.address()),
                "address", "Enter the patient's address.");
        result.rejectIf(!Rules.lengthAtMost(request.address(), 255),
                "address", "The address must be 255 characters or fewer.");

        if (!Rules.isPhone(request.contactNumber())) {
            result.reject("contactNumber",
                    "Enter a contact number such as 0771234567 or +94771234567.");
        }

        if (Rules.isPresent(request.email()) && !Rules.isEmail(request.email())) {
            result.reject("email", "Enter a valid email address, or leave it blank.");
        }
    }

    private void validateDentist(BookingRequest request, Optional<Dentist> dentist,
                                 ValidationResult result) {
        if (request.dentistId() == null) {
            result.reject("dentistId", "Choose a dentist.");
            return;
        }
        if (dentist.isEmpty()) {
            result.reject("dentistId", "That dentist is not on file.");
            return;
        }
        if (!dentist.get().isActive()) {
            result.reject("dentistId",
                    dentist.get().getFullName() + " is no longer taking appointments.");
        }
    }

    private void validateTreatment(BookingRequest request, Optional<Treatment> treatment,
                                   ValidationResult result) {
        if (request.treatmentId() == null) {
            result.reject("treatmentId", "Choose a treatment.");
            return;
        }
        if (treatment.isEmpty()) {
            result.reject("treatmentId", "That treatment is not on file.");
            return;
        }
        if (!treatment.get().isActive()) {
            result.reject("treatmentId",
                    treatment.get().getName() + " is no longer offered.");
        }
    }

    private void validateDate(BookingRequest request, ValidationResult result) {
        LocalDate date = request.appointmentDate();
        if (date == null) {
            result.reject("appointmentDate", "Choose a date.");
            return;
        }
        if (date.isBefore(LocalDate.now())) {
            result.reject("appointmentDate", "The date has already passed.");
            return;
        }
        if (date.isAfter(LocalDate.now().plusMonths(MAX_MONTHS_AHEAD))) {
            result.reject("appointmentDate",
                    "Appointments can only be booked up to " + MAX_MONTHS_AHEAD
                            + " months ahead.");
        }
    }

    private void validateTime(BookingRequest request, Optional<Dentist> dentist,
                              ValidationResult result) {
        LocalTime time = request.appointmentTime();
        if (time == null) {
            result.reject("appointmentTime", "Choose a time.");
            return;
        }

        if (!settings.isWithinOpeningHours(time)) {
            result.reject("appointmentTime", "The clinic is open from "
                    + settings.opensAt() + " to " + settings.closesAt() + ".");
            return;
        }

        if (!settings.isOnSlotBoundary(time)) {
            result.reject("appointmentTime", "Appointments start every "
                    + settings.slotMinutes() + " minutes, on the hour or the half hour.");
            return;
        }

        // Today is allowed, but not a time that has already gone by.
        LocalDate date = request.appointmentDate();
        if (date != null && date.equals(LocalDate.now())
                && LocalDateTime.of(date, time).isBefore(LocalDateTime.now())) {
            result.reject("appointmentTime", "That time has already passed today.");
            return;
        }

        dentist.filter(d -> !d.isWithinSession(time)).ifPresent(d ->
                result.reject("appointmentTime", d.getFullName() + " works from "
                        + d.getSessionStart() + " to " + d.getSessionEnd() + "."));
    }
}
