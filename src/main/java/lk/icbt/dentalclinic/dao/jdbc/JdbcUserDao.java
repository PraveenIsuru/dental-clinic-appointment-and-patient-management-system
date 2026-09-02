package lk.icbt.dentalclinic.dao.jdbc;

import lk.icbt.dentalclinic.dao.RowMapper;
import lk.icbt.dentalclinic.dao.UserDao;
import lk.icbt.dentalclinic.model.Role;
import lk.icbt.dentalclinic.model.RoleCode;
import lk.icbt.dentalclinic.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public final class JdbcUserDao extends AbstractJdbcDao implements UserDao {

    /**
     * Every query joins {@code roles}. A user is never useful without its role — the
     * authorisation filter needs it on every request — so fetching it separately would
     * be an N+1 select for no benefit.
     */
    private static final String SELECT = """
            SELECT u.user_id, u.username, u.password_hash, u.full_name, u.email,
                   u.active, u.failed_login_attempts, u.locked_until, u.last_login_at,
                   r.role_id, r.code AS role_code, r.description AS role_description
            FROM users u
            JOIN roles r ON r.role_id = u.role_id
            """;

    private static final RowMapper<User> MAPPER = rs -> User.builder()
            .userId(rs.getInt("user_id"))
            .username(rs.getString("username"))
            .passwordHash(rs.getString("password_hash"))
            .fullName(rs.getString("full_name"))
            .email(rs.getString("email"))
            .role(new Role(rs.getInt("role_id"),
                    RoleCode.of(rs.getString("role_code")),
                    rs.getString("role_description")))
            .active(rs.getBoolean("active"))
            .failedLoginAttempts(rs.getInt("failed_login_attempts"))
            .lockedUntil(localDateTime(rs, "locked_until"))
            .lastLoginAt(localDateTime(rs, "last_login_at"))
            .build();

    public JdbcUserDao(ConnectionPool pool) {
        super(pool);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        // Case-insensitive by collation (utf8mb4_unicode_ci), so "Admin" finds "admin".
        return queryOne(SELECT + " WHERE u.username = ?", MAPPER, username);
    }

    @Override
    public Optional<User> findById(int userId) {
        return queryOne(SELECT + " WHERE u.user_id = ?", MAPPER, userId);
    }

    @Override
    public List<User> findAll() {
        return query(SELECT + " ORDER BY r.code, u.username", MAPPER);
    }

    @Override
    public boolean existsByUsername(String username) {
        return queryOne("SELECT 1 AS present FROM users WHERE username = ?",
                rs -> rs.getInt("present"), username).isPresent();
    }

    @Override
    public boolean existsByEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return queryOne("SELECT 1 AS present FROM users WHERE email = ?",
                rs -> rs.getInt("present"), email).isPresent();
    }

    @Override
    public int create(User user, String passwordHash) {
        // The hash is passed separately rather than read off the User: the entity has
        // no getter for it, by design. See the note on User.
        return insertReturningKey("""
                        INSERT INTO users (username, password_hash, full_name, email, role_id, active)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                user.getUsername(), passwordHash, user.getFullName(), user.getEmail(),
                user.getRole().getRoleId(), user.isActive());
    }

    @Override
    public void updatePasswordHash(int userId, String passwordHash) {
        update("UPDATE users SET password_hash = ? WHERE user_id = ?", passwordHash, userId);
    }

    @Override
    public int recordFailedAttempt(int userId) {
        // Incremented in SQL rather than read-modify-write in Java, so two simultaneous
        // failed attempts cannot both read the same count and lose one increment.
        update("UPDATE users SET failed_login_attempts = failed_login_attempts + 1 "
                + "WHERE user_id = ?", userId);
        return queryOne("SELECT failed_login_attempts AS n FROM users WHERE user_id = ?",
                rs -> rs.getInt("n"), userId).orElse(0);
    }

    @Override
    public void recordSuccessfulLogin(int userId) {
        update("""
                UPDATE users
                SET failed_login_attempts = 0, locked_until = NULL, last_login_at = NOW()
                WHERE user_id = ?
                """, userId);
    }

    @Override
    public void lockUntil(int userId, LocalDateTime until) {
        update("UPDATE users SET locked_until = ? WHERE user_id = ?", until, userId);
    }
}
