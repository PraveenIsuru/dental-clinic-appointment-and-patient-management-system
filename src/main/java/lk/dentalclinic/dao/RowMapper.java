package lk.dentalclinic.dao;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps the current row of a {@link ResultSet} to a domain object.
 *
 * <p>The variable half of the TEMPLATE METHOD in {@code AbstractJdbcDao}: that class
 * owns the fixed sequence (borrow, prepare, bind, execute, iterate, close), and a
 * mapper supplies the one step that differs per query. It must not call
 * {@link ResultSet#next()} — iteration belongs to the template.
 */
@FunctionalInterface
public interface RowMapper<T> {

    T map(ResultSet rs) throws SQLException;
}
