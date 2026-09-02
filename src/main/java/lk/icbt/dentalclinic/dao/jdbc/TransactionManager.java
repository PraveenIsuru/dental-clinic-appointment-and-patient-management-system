package lk.icbt.dentalclinic.dao.jdbc;

import lk.icbt.dentalclinic.dao.DataAccessException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs a unit of work inside one database transaction.
 *
 * <p>Every DAO called within the lambda shares the same {@link Connection}, published
 * through a {@link ThreadLocal}, so a booking that writes an appointment and advances
 * the number sequence either commits together or rolls back together. This is the
 * hand-written equivalent of Spring's {@code @Transactional}; making the boundary an
 * explicit lambda rather than an annotation means it is visible at the call site and
 * cannot be silently lost by a self-invocation, which is a well-known way to lose a
 * proxy-based transaction.
 *
 * <p>Nested calls join the outer transaction rather than opening a second one.
 */
public final class TransactionManager {

    private static final Logger LOG = Logger.getLogger(TransactionManager.class.getName());
    private static final ThreadLocal<Connection> CURRENT = new ThreadLocal<>();

    private final ConnectionPool pool;

    public TransactionManager(ConnectionPool pool) {
        this.pool = pool;
    }

    /** Work that returns a value and may fail with a checked SQL error. */
    @FunctionalInterface
    public interface TxCallable<T> {
        T call() throws SQLException;
    }

    @FunctionalInterface
    public interface TxRunnable {
        void run() throws SQLException;
    }

    /**
     * The connection bound to the current transaction, if one is in progress.
     * Consulted by {@code AbstractJdbcDao} so DAOs need no transaction argument.
     */
    static Connection currentConnection() {
        return CURRENT.get();
    }

    public static boolean isTransactionActive() {
        return CURRENT.get() != null;
    }

    public <T> T inTransaction(TxCallable<T> work) {
        return inTransactionAs(null, work);
    }

    public void inTransaction(TxRunnable work) {
        inTransactionAs(null, () -> {
            work.run();
            return null;
        });
    }

    /**
     * Runs the work in a transaction, telling the database who is responsible.
     *
     * <p>{@code SET @app_user_id} is read by the audit triggers in
     * {@code V2__routines.sql}, so {@code audit_log.changed_by} records the acting
     * user rather than only the row's original creator.
     *
     * @param actingUserId the signed-in user, or {@code null} for system work
     */
    public <T> T inTransactionAs(Integer actingUserId, TxCallable<T> work) {
        if (isTransactionActive()) {
            // Join the outer transaction. Committing here would release locks the
            // caller still needs and break its rollback.
            try {
                return work.call();
            } catch (SQLException e) {
                throw JdbcExceptions.translate("nested transactional work failed", e);
            }
        }

        try (Connection connection = pool.borrow()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            CURRENT.set(connection);
            try {
                setActingUser(connection, actingUserId);
                T result = work.call();
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(connection);
                if (e instanceof SQLException sqlException) {
                    throw JdbcExceptions.translate("transaction rolled back", sqlException);
                }
                throw (RuntimeException) e;
            } finally {
                CURRENT.remove();
                restoreAutoCommit(connection, previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not obtain a connection for the transaction", e);
        }
    }

    private static void setActingUser(Connection connection, Integer actingUserId)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(actingUserId == null
                    ? "SET @app_user_id = NULL"
                    : "SET @app_user_id = " + actingUserId.intValue());
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Rollback failed; the transaction may be partially applied", e);
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean previous) {
        try {
            connection.setAutoCommit(previous);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Could not restore auto-commit before returning the connection", e);
        }
    }
}
