package lk.icbt.dentalclinic.dao;

import lk.icbt.dentalclinic.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * DAO / REPOSITORY pattern.
 *
 * <p>The business tier depends on this interface, never on the JDBC implementation,
 * so the sign-in logic can be unit tested against an in-memory stand-in with no
 * database at all. That substitutability is what the pattern buys; without it every
 * AuthService test would need a running MySQL.
 */
public interface UserDao {

    Optional<User> findByUsername(String username);

    Optional<User> findById(int userId);

    List<User> findAll();

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** @return the generated user id */
    int create(User user, String passwordHash);

    void updatePasswordHash(int userId, String passwordHash);

    /** Increments the failure counter and returns its new value. */
    int recordFailedAttempt(int userId);

    /** Clears the failure counter and stamps {@code last_login_at}. */
    void recordSuccessfulLogin(int userId);

    void lockUntil(int userId, LocalDateTime until);
}
