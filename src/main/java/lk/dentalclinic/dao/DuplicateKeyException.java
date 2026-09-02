package lk.icbt.dentalclinic.dao;

/**
 * A unique constraint was violated.
 *
 * <p>Raised when MySQL reports error 1062. The case that matters is
 * {@code uq_dentist_slot}: two bookings racing for the same slot both pass the
 * availability check, and the database refuses the second. {@link #constraintName()}
 * lets the service tell that apart from a duplicate username and produce the right
 * message (see the Book Appointment sequence diagram).
 */
public class DuplicateKeyException extends DataAccessException {

    private final String constraintName;

    public DuplicateKeyException(String message, String constraintName, Throwable cause) {
        super(message, cause);
        this.constraintName = constraintName;
    }

    public String constraintName() {
        return constraintName;
    }

    public boolean isDentistSlotClash() {
        return constraintName != null && constraintName.contains("uq_dentist_slot");
    }
}
