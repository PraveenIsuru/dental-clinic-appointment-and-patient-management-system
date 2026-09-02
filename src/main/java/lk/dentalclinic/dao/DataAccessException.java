package lk.dentalclinic.dao;

/**
 * Wraps the checked {@link java.sql.SQLException} so that JDBC does not leak into
 * the business and presentation tiers.
 *
 * <p>This is what lets {@code service} and {@code web} classes avoid importing
 * {@code java.sql} at all — the boundary the architecture test enforces. Subclasses
 * carry the cases callers actually branch on; everything else stays generic.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(String message) {
        super(message);
    }
}
