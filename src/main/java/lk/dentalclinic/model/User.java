package lk.dentalclinic.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A login account.
 *
 * <p><strong>The stored hash is never exposed.</strong> There is no
 * {@code getPasswordHash()}. A caller that wants to check a password asks the user
 * to check it — {@link #passwordMatches(char[], PasswordVerifier)} — so the hash
 * cannot reach a DTO, a log line or a JSON response by accident. This is stronger
 * than the package-private accessor originally drawn in the class diagram; see the
 * amendment recorded in {@code my-docs/task-a/design-decisions.md}.
 *
 * <p>{@code User} is not part of the {@link Person} hierarchy — see A5 and the note
 * on {@link Person}.
 */
public final class User {

    private final int userId;
    private final String username;
    private final String passwordHash;
    private final String fullName;
    private final String email;
    private final Role role;
    private final boolean active;
    private final int failedLoginAttempts;
    private final LocalDateTime lockedUntil;
    private final LocalDateTime lastLoginAt;

    private User(Builder builder) {
        this.userId = builder.userId;
        this.username = Objects.requireNonNull(builder.username, "username");
        this.passwordHash = builder.passwordHash;
        this.fullName = builder.fullName;
        this.email = builder.email;
        this.role = Objects.requireNonNull(builder.role, "role");
        this.active = builder.active;
        this.failedLoginAttempts = builder.failedLoginAttempts;
        this.lockedUntil = builder.lockedUntil;
        this.lastLoginAt = builder.lastLoginAt;
    }

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public boolean hasRole(RoleCode code) {
        return role.getCode() == code;
    }

    /** True while a lock-out from repeated failed sign-ins is still in force. */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    /** True when this account may sign in at all, ignoring the password. */
    public boolean canAuthenticate() {
        return active && !isLocked();
    }

    /**
     * Checks a candidate password. The stored hash never leaves this object.
     *
     * @return false when no hash is stored, so a row with a null hash can never
     *         be signed into
     */
    public boolean passwordMatches(char[] candidate, PasswordVerifier verifier) {
        return passwordHash != null && verifier.verify(candidate, passwordHash);
    }

    /**
     * Whether the stored hash was produced at a weaker cost than the current
     * setting, so the caller can transparently upgrade it after a successful
     * sign-in. Takes a predicate for the same reason as
     * {@link #passwordMatches(char[], PasswordVerifier)}.
     */
    public boolean needsRehash(Predicate<String> staleTest) {
        return staleTest.test(passwordHash);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int userId;
        private String username;
        private String passwordHash;
        private String fullName;
        private String email;
        private Role role;
        private boolean active = true;
        private int failedLoginAttempts;
        private LocalDateTime lockedUntil;
        private LocalDateTime lastLoginAt;

        public Builder userId(int userId) {
            this.userId = userId;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder passwordHash(String passwordHash) {
            this.passwordHash = passwordHash;
            return this;
        }

        public Builder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Builder failedLoginAttempts(int failedLoginAttempts) {
            this.failedLoginAttempts = failedLoginAttempts;
            return this;
        }

        public Builder lockedUntil(LocalDateTime lockedUntil) {
            this.lockedUntil = lockedUntil;
            return this;
        }

        public Builder lastLoginAt(LocalDateTime lastLoginAt) {
            this.lastLoginAt = lastLoginAt;
            return this;
        }

        public User build() {
            return new User(this);
        }
    }

    /** Deliberately omits the hash - this string reaches log files. */
    @Override
    public String toString() {
        return "User[" + username + ", " + role + "]";
    }
}
