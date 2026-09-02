package lk.icbt.dentalclinic.service;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A booking submission — brief requirement 2's field list, as typed.
 *
 * <p>The patient may arrive two ways. An administrator booking at the desk usually
 * types a new patient's details, so {@code patientId} is {@code null} and
 * {@code patientName}, {@code address} and {@code contactNumber} carry the record to
 * create. A returning patient, or a patient booking for themselves, comes in with
 * {@code patientId} set and the typed fields ignored.
 *
 * <p>Both paths are one request object rather than two overloads, because the
 * validation, the availability check and the slot suggestion are identical either way
 * and only the first step differs.
 */
public record BookingRequest(Integer patientId,
                             String patientName,
                             String address,
                             String contactNumber,
                             String email,
                             Integer dentistId,
                             Integer treatmentId,
                             LocalDate appointmentDate,
                             LocalTime appointmentTime,
                             String notes) {

    public boolean isForExistingPatient() {
        return patientId != null;
    }

    /** A copy naming an existing patient — used when a patient books for themselves. */
    public BookingRequest forPatient(int existingPatientId) {
        return new BookingRequest(existingPatientId, patientName, address, contactNumber,
                email, dentistId, treatmentId, appointmentDate, appointmentTime, notes);
    }
}
