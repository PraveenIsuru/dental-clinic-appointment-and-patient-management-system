package lk.icbt.dentalclinic.service;

/**
 * No bill with that number is visible to the caller.
 *
 * <p>As with {@link AppointmentNotFoundException}, also thrown when the bill exists but
 * belongs to another patient. Handlers map it to 404, never 403, so the response cannot
 * be used to discover which bill numbers are real (A6).
 */
public class BillNotFoundException extends RuntimeException {

    public BillNotFoundException(String billNo) {
        super("No bill found for '" + billNo + "'");
    }
}
