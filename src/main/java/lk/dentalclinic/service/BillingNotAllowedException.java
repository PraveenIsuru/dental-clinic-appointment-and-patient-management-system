package lk.dentalclinic.service;

/**
 * The bill cannot be issued in this state — the appointment is not completed, one has
 * already been issued, or the caller may not bill at all.
 *
 * <p>Distinct from {@link ValidationException}, which means a field was wrong and the
 * form should be redisplayed. This means the request was well formed but the world is
 * not in a state that permits it, so handlers map it to <strong>409 Conflict</strong>.
 * The message is written for the person at the counter and is shown verbatim.
 */
public class BillingNotAllowedException extends RuntimeException {

    public BillingNotAllowedException(String message) {
        super(message);
    }
}
