package lk.icbt.dentalclinic.dao;

import lk.icbt.dentalclinic.model.Role;
import lk.icbt.dentalclinic.model.RoleCode;
import lk.icbt.dentalclinic.model.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An in-memory {@link UserDao} for tests.
 *
 * <p>This class is the concrete argument for the DAO pattern. Because
 * {@code AuthService} depends on the interface rather than on {@code JdbcUserDao},
 * every sign-in rule — lock-out counting, the generic failure message, session
 * fixation, transparent re-hashing — is testable with no MySQL running, in
 * milliseconds. Written by hand rather than with Mockito: at this size a real
 * implementation is shorter than the stubbing would be, and it can enforce its own
 * invariants (a caller cannot "verify" an interaction that never made sense).
 */
public final class InMemoryUserDao implements UserDao {

    public static final Role ADMIN_ROLE = new Role(1, RoleCode.ADMIN, "Administrator");
    public static final Role DENTIST_ROLE = new Role(2, RoleCode.DENTIST, "Dentist");
    public static final Role PATIENT_ROLE = new Role(3, RoleCode.PATIENT, "Patient");

    private final Map<Integer, User> byId = new LinkedHashMap<>();
    private final Map<Integer, String> hashes = new LinkedHashMap<>();
    private int nextId = 1;

    /** Records every hash written back, so the re-hash-on-login path can be asserted. */
    public final List<String> passwordUpdates = new ArrayList<>();

    public User add(String username, String passwordHash, Role role) {
        int id = nextId++;
        User user = User.builder()
                .userId(id)
                .username(username)
                .passwordHash(passwordHash)
                .fullName(username + " Example")
                .email(username + "@example.lk")
                .role(role)
                .active(true)
                .build();
        byId.put(id, user);
        hashes.put(id, passwordHash);
        return user;
    }

    /** Replaces a user wholesale, for setting up a locked or disabled account. */
    public void replace(User user) {
        byId.put(user.getUserId(), user);
    }

    public User get(int userId) {
        return byId.get(userId);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return byId.values().stream()
                .filter(user -> user.getUsername().equalsIgnoreCase(username))
                .findFirst();
    }

    @Override
    public Optional<User> findById(int userId) {
        return Optional.ofNullable(byId.get(userId));
    }

    @Override
    public List<User> findAll() {
        return List.copyOf(byId.values());
    }

    @Override
    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    @Override
    public boolean existsByEmail(String email) {
        return byId.values().stream()
                .anyMatch(user -> email != null && email.equalsIgnoreCase(user.getEmail()));
    }

    @Override
    public int create(User user, String passwordHash) {
        int id = nextId++;
        User stored = rebuild(user).userId(id).build();
        byId.put(id, stored);
        hashes.put(id, passwordHash);
        return id;
    }

    @Override
    public void updatePasswordHash(int userId, String passwordHash) {
        hashes.put(userId, passwordHash);
        passwordUpdates.add(passwordHash);
        byId.computeIfPresent(userId,
                (id, user) -> rebuild(user).passwordHash(passwordHash).build());
    }

    @Override
    public int recordFailedAttempt(int userId) {
        User user = byId.get(userId);
        int attempts = user.getFailedLoginAttempts() + 1;
        byId.put(userId, rebuild(user).failedLoginAttempts(attempts).build());
        return attempts;
    }

    @Override
    public void recordSuccessfulLogin(int userId) {
        byId.computeIfPresent(userId, (id, user) -> rebuild(user)
                .failedLoginAttempts(0)
                .lockedUntil(null)
                .lastLoginAt(LocalDateTime.now())
                .build());
    }

    @Override
    public void lockUntil(int userId, LocalDateTime until) {
        byId.computeIfPresent(userId, (id, user) -> rebuild(user).lockedUntil(until).build());
    }

    /** Copies a user into a fresh builder, carrying the hash the entity will not expose. */
    private User.Builder rebuild(User user) {
        return User.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .passwordHash(hashes.get(user.getUserId()))
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .active(user.isActive())
                .failedLoginAttempts(user.getFailedLoginAttempts())
                .lockedUntil(user.getLockedUntil())
                .lastLoginAt(user.getLastLoginAt());
    }
}
