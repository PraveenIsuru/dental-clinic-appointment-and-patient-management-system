package lk.dentalclinic.dao.jdbc;

import lk.dentalclinic.dao.DataAccessException;
import lk.dentalclinic.dao.RowMapper;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * TEMPLATE METHOD — the fixed half of every JDBC call.
 *
 * <p>This class owns the invariant sequence: obtain a connection, prepare the
 * statement, bind the parameters, execute, iterate the result set, close everything in
 * the right order, and translate any {@link SQLException}. Subclasses supply only the
 * SQL and a {@link RowMapper}.
 *
 * <p><em>What it is worth:</em> the reference project repeated a nine-line
 * try-with-resources block in every method of every DAO, and each copy was a chance to
 * close a {@code ResultSet} but leak a {@code Connection}. Here that code exists once.
 * The cost is a layer of indirection — a reader chasing a query has to know this class
 * exists — which is a fair trade at nine DAOs and would not have been at two.
 *
 * <p><strong>Transaction awareness.</strong> When a {@link TransactionManager} has bound
 * a connection to the thread, every method below joins it and must <em>not</em> close it;
 * the transaction owns its lifetime. Otherwise a connection is borrowed from the pool and
 * closed here, which returns it to the pool through the pool's proxy.
 */
public abstract class AbstractJdbcDao {

    protected final ConnectionPool pool;

    protected AbstractJdbcDao(ConnectionPool pool) {
        this.pool = pool;
    }

    // ------------------------------------------------------------------ queries

    /** Runs a query and maps every row. */
    protected <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        Connection joined = TransactionManager.currentConnection();
        if (joined != null) {
            return doQuery(joined, sql, mapper, params);
        }
        try (Connection connection = pool.borrow()) {
            return doQuery(connection, sql, mapper, params);
        } catch (SQLException e) {
            throw JdbcExceptions.translate("query failed: " + sql, e);
        }
    }

    /** Runs a query expected to match at most one row. */
    protected <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> results = query(sql, mapper, params);
        if (results.size() > 1) {
            throw new DataAccessException(
                    "Expected at most one row but got " + results.size() + " from: " + sql);
        }
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** Runs an INSERT, UPDATE or DELETE and returns the affected row count. */
    protected int update(String sql, Object... params) {
        Connection joined = TransactionManager.currentConnection();
        if (joined != null) {
            return doUpdate(joined, sql, params);
        }
        try (Connection connection = pool.borrow()) {
            return doUpdate(connection, sql, params);
        } catch (SQLException e) {
            throw JdbcExceptions.translate("update failed: " + sql, e);
        }
    }

    /** Runs an INSERT and returns the generated auto-increment key. */
    protected int insertReturningKey(String sql, Object... params) {
        Connection joined = TransactionManager.currentConnection();
        if (joined != null) {
            return doInsert(joined, sql, params);
        }
        try (Connection connection = pool.borrow()) {
            return doInsert(connection, sql, params);
        } catch (SQLException e) {
            throw JdbcExceptions.translate("insert failed: " + sql, e);
        }
    }

    /**
     * Runs work against a raw connection, for the cases the helpers above do not
     * cover — chiefly {@link java.sql.CallableStatement} for the stored procedures.
     */
    protected <T> T withConnection(ConnectionCallback<T> callback) {
        Connection joined = TransactionManager.currentConnection();
        if (joined != null) {
            try {
                return callback.run(joined);
            } catch (SQLException e) {
                throw JdbcExceptions.translate("statement failed", e);
            }
        }
        try (Connection connection = pool.borrow()) {
            return callback.run(connection);
        } catch (SQLException e) {
            throw JdbcExceptions.translate("statement failed", e);
        }
    }

    @FunctionalInterface
    protected interface ConnectionCallback<T> {
        T run(Connection connection) throws SQLException;
    }

    // ------------------------------------------------------------------ internals

    private <T> List<T> doQuery(Connection connection, String sql, RowMapper<T> mapper,
                                Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw JdbcExceptions.translate("query failed: " + sql, e);
        }
    }

    private int doUpdate(Connection connection, String sql, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw JdbcExceptions.translate("update failed: " + sql, e);
        }
    }

    private int doInsert(Connection connection, String sql, Object... params) {
        try (PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, params);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new DataAccessException("Insert returned no generated key: " + sql);
            }
        } catch (SQLException e) {
            throw JdbcExceptions.translate("insert failed: " + sql, e);
        }
    }

    /**
     * Binds parameters positionally.
     *
     * <p>Everything goes through {@link PreparedStatement}; no SQL is ever built by
     * string concatenation, which is what makes the application structurally immune to
     * SQL injection rather than merely careful about it.
     */
    protected static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            int index = i + 1;
            Object value = params[i];
            switch (value) {
                case null -> statement.setNull(index, java.sql.Types.NULL);
                case String s -> statement.setString(index, s);
                case Integer n -> statement.setInt(index, n);
                case Long n -> statement.setLong(index, n);
                case Boolean b -> statement.setBoolean(index, b);
                case BigDecimal d -> statement.setBigDecimal(index, d);
                case LocalDate d -> statement.setDate(index, Date.valueOf(d));
                case LocalTime t -> statement.setTime(index, Time.valueOf(t));
                case LocalDateTime dt -> statement.setTimestamp(index, Timestamp.valueOf(dt));
                case Enum<?> e -> statement.setString(index, e.name());
                default -> statement.setObject(index, value);
            }
        }
    }

    // ------------------------------------------------------ null-safe column readers

    /** {@code getInt} returns 0 for SQL NULL; this distinguishes the two. */
    protected static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    protected static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    protected static LocalTime localTime(ResultSet rs, String column) throws SQLException {
        Time value = rs.getTime(column);
        return value == null ? null : value.toLocalTime();
    }

    protected static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
