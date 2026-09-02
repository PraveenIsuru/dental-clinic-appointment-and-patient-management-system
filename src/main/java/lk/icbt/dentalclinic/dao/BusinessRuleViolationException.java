package lk.icbt.dentalclinic.dao;

/**
 * A rule enforced by a database trigger rejected the statement.
 *
 * <p>Raised when MySQL reports SQLSTATE 45000, which is what {@code SIGNAL} produces
 * — currently only {@code trg_bill_before_insert} refusing a discount above 25%
 * (A10). The trigger's own {@code MESSAGE_TEXT} is carried through, so the message
 * shown to the user is written once, in SQL.
 */
public class BusinessRuleViolationException extends DataAccessException {

    public BusinessRuleViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
