package lk.icbt.dentalclinic.service;

/**
 * No appointment with that number is visible to the caller.
 *
 * <p>Deliberately also thrown when the appointment exists but belongs to someone else
 * (assumption A6). Handlers map it to <strong>404</strong>, never 403: a 403 would
 * confirm that the number is real, and appointment numbers are sequential, so an
 * attacker could count the clinic's bookings by watching which numbers answer
 * differently. 403 is reserved for role-level refusals, where nothing is revealed.
 */
public class AppointmentNotFoundException extends RuntimeException {

    public AppointmentNotFoundException(String appointmentNo) {
        super("No appointment found for '" + appointmentNo + "'");
    }
}
