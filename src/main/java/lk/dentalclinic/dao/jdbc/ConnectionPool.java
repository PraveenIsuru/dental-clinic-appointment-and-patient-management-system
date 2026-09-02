package lk.dentalclinic.dao.jdbc;

import lk.dentalclinic.config.AppConfig;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A bounded JDBC connection pool.
 *
 * <p>Three patterns meet here.
 *
 * <p><strong>OBJECT POOL.</strong> Opening a MySQL connection costs a TCP handshake,
 * authentication and session setup — tens of milliseconds. The reference project called
 * {@code DriverManager.getConnection()} on every single query, which meant a page
 * rendering ten queries paid that cost ten times. Connections are expensive to create,
 * cheap to reuse and interchangeable, which is exactly the situation the pattern exists
 * for.
 *
 * <p><strong>SINGLETON.</strong> A second pool would silently double the connection
 * count and could exceed MySQL's {@code max_connections}. The instance is created once
 * via {@link #initialise(AppConfig)} and read through {@link #getInstance()}.
 * <em>Evaluated honestly:</em> a singleton is global mutable state and makes tests
 * order-dependent, which is why {@link #initialise(AppConfig)} is idempotent-by-replacement
 * and {@link #shutdown()} exists — the test suite can reset it. Constructor injection of a
 * pool instance would be purer; the singleton was kept because every DAO needs it and
 * threading it through every constructor added noise without adding safety.
 *
 * <p><strong>PROXY.</strong> {@link #borrow()} hands back a {@link Proxy} implementing
 * {@link Connection} whose {@code close()} returns the connection to the pool instead of
 * closing the socket. Callers therefore write ordinary try-with-resources and cannot leak
 * a connection by forgetting a bespoke {@code release()} call.
 */
public final class ConnectionPool implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ConnectionPool.class.getName());
    private static final int BORROW_TIMEOUT_SECONDS = 10;
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private static volatile ConnectionPool instance;

    private final String url;
    private final String user;
    private final String password;
    private final int maxSize;

    private final BlockingQueue<Connection> idle;
    private final AtomicInteger liveConnections = new AtomicInteger();
    private volatile boolean closed;

    private ConnectionPool(String url, String user, String password, int maxSize) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.maxSize = maxSize;
        this.idle = new ArrayBlockingQueue<>(maxSize);
    }

    // ------------------------------------------------------------------ lifecycle

    /** Creates the pool, replacing any existing one. Called once at startup. */
    public static synchronized ConnectionPool initialise(AppConfig config) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new ConnectionPool(
                config.get("db.url"),
                config.get("db.user"),
                config.get("db.password"),
                config.getInt("db.pool.size"));
        return instance;
    }

    public static ConnectionPool getInstance() {
        ConnectionPool pool = instance;
        if (pool == null) {
            throw new IllegalStateException(
                    "ConnectionPool.initialise(config) must be called before getInstance()");
        }
        return pool;
    }

    /** Opens one connection to prove the credentials and the schema are reachable. */
    public void verifyConnectivity() throws SQLException {
        try (Connection connection = borrow()) {
            if (!connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                throw new SQLException("Validation query failed against " + url);
            }
        }
    }

    // ------------------------------------------------------------------ borrowing

    /**
     * Takes a connection from the pool, opening a new one if the pool is below its
     * limit, otherwise waiting for one to be returned.
     *
     * <p>The returned object is a proxy: calling {@code close()} on it returns the
     * connection here rather than closing it.
     *
     * @throws SQLException if no connection becomes available within the timeout,
     *                      which indicates a leak or a saturated pool rather than a
     *                      transient error, so it is not retried
     */
    public Connection borrow() throws SQLException {
        if (closed) {
            throw new SQLException("Connection pool is closed");
        }

        Connection connection = idle.poll();

        while (connection != null && !isUsable(connection)) {
            discard(connection);
            connection = idle.poll();
        }

        if (connection == null) {
            connection = openIfUnderLimit();
        }

        if (connection == null) {
            connection = waitForReturn();
        }

        return wrap(connection);
    }

    private Connection openIfUnderLimit() throws SQLException {
        // Claim a slot first, so two threads cannot both decide there is room for one more.
        int claimed = liveConnections.incrementAndGet();
        if (claimed > maxSize) {
            liveConnections.decrementAndGet();
            return null;
        }
        try {
            Connection connection = DriverManager.getConnection(url, user, password);
            connection.setAutoCommit(true);
            LOG.fine(() -> "Opened pooled connection " + claimed + "/" + maxSize);
            return connection;
        } catch (SQLException e) {
            liveConnections.decrementAndGet();
            throw e;
        }
    }

    private Connection waitForReturn() throws SQLException {
        try {
            Connection connection = idle.poll(BORROW_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (connection == null) {
                throw new SQLException("Timed out after " + BORROW_TIMEOUT_SECONDS
                        + "s waiting for a connection; all " + maxSize + " are in use");
            }
            if (!isUsable(connection)) {
                discard(connection);
                Connection replacement = openIfUnderLimit();
                if (replacement == null) {
                    throw new SQLException("Could not replace a dead pooled connection");
                }
                return replacement;
            }
            return connection;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for a connection", e);
        }
    }

    // ------------------------------------------------------------------ returning

    private void release(Connection real) {
        if (closed || !isUsable(real)) {
            discard(real);
            return;
        }
        try {
            // A caller that began a transaction and neither committed nor rolled back
            // must not hand the next borrower a dirty session.
            if (!real.getAutoCommit()) {
                real.rollback();
                real.setAutoCommit(true);
            }
            real.clearWarnings();
            if (!idle.offer(real)) {
                discard(real);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to reset a connection on return; discarding it", e);
            discard(real);
        }
    }

    private void discard(Connection connection) {
        liveConnections.decrementAndGet();
        try {
            connection.close();
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Ignoring failure while closing a discarded connection", e);
        }
    }

    private static boolean isUsable(Connection connection) {
        try {
            return !connection.isClosed() && connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Wraps a connection so {@code close()} returns it to the pool.
     *
     * <p>{@link Connection} is an interface, so a JDK dynamic proxy suffices and no
     * bytecode library is needed.
     */
    private Connection wrap(Connection real) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "close" -> {
                            release(real);
                            return null;
                        }
                        case "isClosed" -> {
                            return false;
                        }
                        case "unwrap" -> {
                            return real;
                        }
                        default -> {
                            try {
                                return method.invoke(real, args);
                            } catch (InvocationTargetException e) {
                                // Unwrap, or every SQLException would surface as an
                                // UndeclaredThrowableException and callers could not catch it.
                                throw e.getCause();
                            }
                        }
                    }
                });
    }

    // ------------------------------------------------------------------ shutdown

    public int idleCount() {
        return idle.size();
    }

    public int liveCount() {
        return liveConnections.get();
    }

    public int maxSize() {
        return maxSize;
    }

    public boolean isClosed() {
        return closed;
    }

    public synchronized void shutdown() {
        closed = true;
        List<Connection> drained = new ArrayList<>();
        idle.drainTo(drained);
        for (Connection connection : drained) {
            discard(connection);
        }
        LOG.info(() -> "Connection pool shut down; " + drained.size() + " idle connections closed");
    }

    @Override
    public void close() {
        shutdown();
    }
}
