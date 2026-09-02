package lk.icbt.dentalclinic.validation;

import lk.icbt.dentalclinic.model.Dentist;
import lk.icbt.dentalclinic.model.Treatment;
import lk.icbt.dentalclinic.model.TreatmentFamily;
import lk.icbt.dentalclinic.service.BookingRequest;
import lk.icbt.dentalclinic.service.ClinicSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The booking rules of brief requirement 2, with boundary data.
 *
 * <p>Clinic hours are 08:00–20:00 in 30-minute slots, and the dentist under test works
 * 08:00–16:00, so the two ranges differ — a time can be inside the clinic's hours and
 * still outside the dentist's, which is exactly the case a single range would miss.
 */
class AppointmentValidatorTest {

    private static final ClinicSettings SETTINGS = new ClinicSettings(
            new BigDecimal("2500.00"), BigDecimal.ZERO, new BigDecimal("25.00"),
            LocalTime.of(8, 0), LocalTime.of(20, 0), 30,
            "Sunrise Dental Clinic", "221 Galle Road, Colombo 03");

    private static final Dentist DENTIST = Dentist.builder()
            .id(1).fullName("Dr. Nimal Perera").specialization("General Dentistry")
            .sessionStart(LocalTime.of(8, 0)).sessionEnd(LocalTime.of(16, 0))
            .active(true).build();

    private static final Treatment TREATMENT = new Treatment(1, "CLEAN",
            "Scaling and Polishing", TreatmentFamily.CLEANING, null,
            new BigDecimal("5000.00"), 30, true);

    private final AppointmentValidator validator = new AppointmentValidator(SETTINGS);

    private static BookingRequest request(LocalDate date, LocalTime time) {
        return new BookingRequest(null, "Kasun Fernando", "14/3 Temple Road, Nugegoda",
                "0771234567", null, 1, 1, date, time, null);
    }

    private ValidationResult validate(BookingRequest request) {
        return validator.validate(request, Optional.of(DENTIST), Optional.of(TREATMENT));
    }

    private ValidationResult validateAt(LocalTime time) {
        return validate(request(LocalDate.now().plusDays(1), time));
    }

    @Test
    @DisplayName("a well-formed booking passes")
    void acceptsValidBooking() {
        ValidationResult result = validateAt(LocalTime.of(9, 0));

        assertTrue(result.isValid(), result.toString());
    }

    @Nested
    @DisplayName("date rules")
    class Dates {

        @Test
        @DisplayName("yesterday is refused, today and tomorrow are not")
        void pastDatesRefused() {
            assertTrue(validate(request(LocalDate.now().minusDays(1), LocalTime.of(9, 0)))
                    .errorFor("appointmentDate").isPresent());
            // Today is allowed; the time rules handle "already gone by".
            assertTrue(validate(request(LocalDate.now(), LocalTime.of(23, 0)))
                    .errorFor("appointmentDate").isEmpty());
            assertTrue(validate(request(LocalDate.now().plusDays(1), LocalTime.of(9, 0)))
                    .isValid());
        }

        @Test
        @DisplayName("six months ahead is the boundary")
        void tooFarAheadRefused() {
            LocalDate limit = LocalDate.now().plusMonths(AppointmentValidator.MAX_MONTHS_AHEAD);

            assertTrue(validate(request(limit, LocalTime.of(9, 0)))
                    .errorFor("appointmentDate").isEmpty(), "exactly six months is allowed");
            assertTrue(validate(request(limit.plusDays(1), LocalTime.of(9, 0)))
                    .errorFor("appointmentDate").isPresent(), "a day beyond is not");
        }

        @Test
        @DisplayName("a missing date is reported, not assumed")
        void missingDateRefused() {
            assertTrue(validate(request(null, LocalTime.of(9, 0)))
                    .errorFor("appointmentDate").isPresent());
        }

        @Test
        @DisplayName("a time earlier today is refused")
        void pastTimeTodayRefused() {
            // Only meaningful once the clinic has opened; before 08:30 there is no
            // earlier slot to test against.
            if (LocalTime.now().isAfter(LocalTime.of(8, 30))) {
                assertTrue(validate(request(LocalDate.now(), LocalTime.of(8, 0)))
                        .errorFor("appointmentTime").isPresent());
            }
        }
    }

    @Nested
    @DisplayName("time rules")
    class Times {

        @ParameterizedTest
        @DisplayName("times inside clinic hours and on the boundary are accepted")
        @ValueSource(strings = {"08:00", "08:30", "09:00", "12:30", "15:30"})
        void acceptsSlotTimes(String time) {
            assertTrue(validateAt(LocalTime.parse(time)).isValid(), time);
        }

        @ParameterizedTest
        @DisplayName("times outside clinic hours are refused")
        @ValueSource(strings = {"07:30", "07:59", "20:00", "20:30", "23:30"})
        void refusesTimesOutsideOpeningHours(String time) {
            assertTrue(validateAt(LocalTime.parse(time)).errorFor("appointmentTime").isPresent(),
                    time + " should be outside 08:00-20:00");
        }

        @Test
        @DisplayName("08:00 is open and 20:00 is closed — the end of the range is exclusive")
        void openingBoundaryIsInclusiveAndClosingExclusive() {
            // Tested against a dentist working the full day, so this isolates the
            // clinic-hours rule from the dentist-session rule. With the 08:00-16:00
            // dentist, 19:30 would be refused for the wrong reason.
            Dentist allDay = Dentist.builder()
                    .id(3).fullName("Dr. All Day").specialization("General")
                    .sessionStart(LocalTime.of(8, 0)).sessionEnd(LocalTime.of(20, 0))
                    .active(true).build();

            LocalDate tomorrow = LocalDate.now().plusDays(1);

            assertTrue(validator.validate(request(tomorrow, LocalTime.of(8, 0)),
                    Optional.of(allDay), Optional.of(TREATMENT)).isValid(),
                    "08:00 is the first bookable slot");
            assertTrue(validator.validate(request(tomorrow, LocalTime.of(19, 30)),
                    Optional.of(allDay), Optional.of(TREATMENT)).isValid(),
                    "19:30 is the last bookable slot");
            assertTrue(validator.validate(request(tomorrow, LocalTime.of(20, 0)),
                    Optional.of(allDay), Optional.of(TREATMENT))
                    .errorFor("appointmentTime").isPresent(),
                    "20:00 is the closing time, not a slot");
        }

        @ParameterizedTest
        @DisplayName("times off the 30-minute boundary are refused")
        @ValueSource(strings = {"09:05", "09:15", "09:31", "10:45", "11:01"})
        void refusesOffBoundaryTimes(String time) {
            ValidationResult result = validateAt(LocalTime.parse(time));

            assertTrue(result.errorFor("appointmentTime").isPresent(), time);
            assertTrue(result.errorFor("appointmentTime").orElseThrow().contains("30 minutes"));
        }

        @Test
        @DisplayName("a time inside clinic hours but outside the dentist's session is refused")
        void refusesTimeOutsideDentistSession() {
            // 16:30 is within 08:00-20:00 but the dentist works until 16:00.
            ValidationResult result = validateAt(LocalTime.of(16, 30));

            assertTrue(result.errorFor("appointmentTime").isPresent());
            assertTrue(result.errorFor("appointmentTime").orElseThrow().contains("Nimal Perera"),
                    "the message should name the dentist and their hours");
        }

        @Test
        @DisplayName("15:30 is the dentist's last slot; 16:00 is not")
        void dentistSessionEndIsExclusive() {
            assertTrue(validateAt(LocalTime.of(15, 30)).isValid());
            assertTrue(validateAt(LocalTime.of(16, 0)).errorFor("appointmentTime").isPresent());
        }
    }

    @Nested
    @DisplayName("patient rules")
    class PatientFields {

        @Test
        @DisplayName("a new patient needs name, address and a valid contact number")
        void requiresPatientDetails() {
            BookingRequest blank = new BookingRequest(null, "", "", "not-a-phone", null,
                    1, 1, LocalDate.now().plusDays(1), LocalTime.of(9, 0), null);

            ValidationResult result = validate(blank);

            assertTrue(result.errorFor("patientName").isPresent());
            assertTrue(result.errorFor("address").isPresent());
            assertTrue(result.errorFor("contactNumber").isPresent());
            assertEquals(3, result.errors().size(),
                    "all three should be reported together, not one per round trip");
        }

        @Test
        @DisplayName("an existing patient's details are not re-validated")
        void existingPatientSkipsFieldChecks() {
            BookingRequest existing = new BookingRequest(7, null, null, null, null,
                    1, 1, LocalDate.now().plusDays(1), LocalTime.of(9, 0), null);

            assertTrue(validate(existing).isValid());
        }

        @Test
        @DisplayName("a malformed optional email is refused, a blank one is not")
        void emailIsOptionalButMustBeValid() {
            BookingRequest withBadEmail = new BookingRequest(null, "Kasun", "14/3 Temple Road",
                    "0771234567", "not-an-email", 1, 1,
                    LocalDate.now().plusDays(1), LocalTime.of(9, 0), null);
            assertTrue(validate(withBadEmail).errorFor("email").isPresent());

            BookingRequest withoutEmail = new BookingRequest(null, "Kasun", "14/3 Temple Road",
                    "0771234567", "", 1, 1,
                    LocalDate.now().plusDays(1), LocalTime.of(9, 0), null);
            assertTrue(validate(withoutEmail).isValid());
        }
    }

    @Nested
    @DisplayName("dentist and treatment rules")
    class Selections {

        @Test
        @DisplayName("an unknown dentist or treatment is refused")
        void unknownSelectionsRefused() {
            ValidationResult result = validator.validate(
                    request(LocalDate.now().plusDays(1), LocalTime.of(9, 0)),
                    Optional.empty(), Optional.empty());

            assertTrue(result.errorFor("dentistId").isPresent());
            assertTrue(result.errorFor("treatmentId").isPresent());
        }

        @Test
        @DisplayName("a retired dentist cannot be booked")
        void inactiveDentistRefused() {
            Dentist retired = Dentist.builder()
                    .id(2).fullName("Dr. Retired").specialization("General")
                    .sessionStart(LocalTime.of(8, 0)).sessionEnd(LocalTime.of(16, 0))
                    .active(false).build();

            ValidationResult result = validator.validate(
                    request(LocalDate.now().plusDays(1), LocalTime.of(9, 0)),
                    Optional.of(retired), Optional.of(TREATMENT));

            assertTrue(result.errorFor("dentistId").isPresent());
        }

        @Test
        @DisplayName("a retired treatment cannot be booked")
        void inactiveTreatmentRefused() {
            Treatment retired = new Treatment(9, "OLD", "Discontinued",
                    TreatmentFamily.COSMETIC, null, BigDecimal.TEN, 30, false);

            ValidationResult result = validator.validate(
                    request(LocalDate.now().plusDays(1), LocalTime.of(9, 0)),
                    Optional.of(DENTIST), Optional.of(retired));

            assertTrue(result.errorFor("treatmentId").isPresent());
        }

        @Test
        @DisplayName("a missing selection is reported as such")
        void missingSelectionsRefused() {
            BookingRequest nothingChosen = new BookingRequest(null, "Kasun",
                    "14/3 Temple Road", "0771234567", null, null, null,
                    LocalDate.now().plusDays(1), LocalTime.of(9, 0), null);

            ValidationResult result = validator.validate(nothingChosen,
                    Optional.empty(), Optional.empty());

            assertTrue(result.errorFor("dentistId").orElseThrow().contains("Choose a dentist"));
            assertTrue(result.errorFor("treatmentId").orElseThrow().contains("Choose a treatment"));
        }
    }

    @Test
    @DisplayName("notes longer than 500 characters are refused")
    void overlongNotesRefused() {
        BookingRequest longNotes = new BookingRequest(7, null, null, null, null, 1, 1,
                LocalDate.now().plusDays(1), LocalTime.of(9, 0), "x".repeat(501));

        assertTrue(validate(longNotes).errorFor("notes").isPresent());
        assertFalse(validate(new BookingRequest(7, null, null, null, null, 1, 1,
                LocalDate.now().plusDays(1), LocalTime.of(9, 0), "x".repeat(500)))
                .errorFor("notes").isPresent());
    }
}
