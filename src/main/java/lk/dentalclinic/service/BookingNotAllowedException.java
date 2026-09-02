package lk.dentalclinic.service;

/**
 * The booking is well formed and the slot is free, but a clinic policy refuses it.
 *
 * <p>Currently only the upcoming-booking limit. Distinct from {@link ValidationException},
 * which means a field is wrong and the form should be redisplayed with it marked: here
 * nothing the user typed is wrong, so handlers map it to <strong>409 Conflict</strong> and
 * show the message as written.
 */
public class BookingNotAllowedException extends RuntimeException {

    public BookingNotAllowedException(String message) {
        super(message);
    }
}
