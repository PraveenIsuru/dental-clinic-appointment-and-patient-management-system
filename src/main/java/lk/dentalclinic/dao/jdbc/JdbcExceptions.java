package lk.icbt.dentalclinic.dao.jdbc;

import lk.icbt.dentalclinic.dao.BusinessRuleViolationException;
import lk.icbt.dentalclinic.dao.DataAccessException;
import lk.icbt.dentalclinic.dao.DuplicateKeyException;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates vendor SQL errors into the tier-neutral exceptions in {@code dao}.
 *
 * <p>Everything above the data tier catches {@code DataAccessException} and its
 * subclasses; nothing outside this package needs to know a MySQL error code. That
 * keeps the constraint-violation handling drawn in the Book Appointment sequence
 * diagram in one place instead of repeated in every DAO.
 */
final class JdbcExceptions {

    /** MySQL: duplicate entry for a unique key. */
    private static final int ER_DUP_ENTRY = 1062;
    /** MySQL: raised by SIGNAL inside a trigger. */
    private static final String SQLSTATE_SIGNAL = "45000";

    /** Extracts {@code uq_dentist_slot} from "Duplicate entry '…' for key 'appointments.uq_dentist_slot'". */
    private static final Pattern KEY_NAME = Pattern.compile("for key '([^']+)'");

    private JdbcExceptions() {
    }

    static DataAccessException translate(String context, SQLException e) {
        if (e.getErrorCode() == ER_DUP_ENTRY || e instanceof SQLIntegrityConstraintViolationException) {
            String key = constraintNameOf(e.getMessage());
            if (key != null) {
                return new DuplicateKeyException(context + ": duplicate value for " + key, key, e);
            }
        }
        if (SQLSTATE_SIGNAL.equals(e.getSQLState())) {
            // The trigger's own MESSAGE_TEXT is already user-facing; keep it verbatim.
            return new BusinessRuleViolationException(e.getMessage(), e);
        }
        return new DataAccessException(context + ": " + e.getMessage(), e);
    }

    private static String constraintNameOf(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = KEY_NAME.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }
}
