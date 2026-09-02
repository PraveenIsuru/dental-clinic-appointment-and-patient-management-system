package lk.dentalclinic.security;

import lk.dentalclinic.model.RoleCode;
import lk.dentalclinic.model.User;

import java.time.Duration;
import java.time.Instant;

/**
 * One signed-in user's server-side session.
 *
 * <p>Only an opaque random identifier travels to the browser; the identity itself
 * never leaves the server. That is the difference between this and a self-contained
 * cookie or JWT: a session can be revoked immediately, because invalidating the
 * server-side entry is enough.
 *
 * <p>A snapshot of the user's identity is copied in at sign-in rather than a
 * reference to a {@link User} being held, so a stale in-memory object cannot be
 * written back to the database later.
 */
public final class Session {

    private final String id;
    private final int userId;
    private final String username;
    private final String fullName;
    private final RoleCode role;
    private final String csrfToken;
    private final Instant createdAt;

    private volatile Instant lastAccessedAt;

    Session(String id, User user, String csrfToken, Instant createdAt) {
        this.id = id;
        this.userId = user.getUserId();
        this.username = user.getUsername();
        this.fullName = user.getFullName();
        this.role = user.getRole().getCode();
        this.csrfToken = csrfToken;
        this.createdAt = createdAt;
        this.lastAccessedAt = createdAt;
    }

    public String getId() {
        return id;
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

    public RoleCode getRole() {
        return role;
    }

    public String getCsrfToken() {
        return csrfToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public boolean hasRole(RoleCode required) {
        return role == required;
    }

    public boolean hasAnyRole(RoleCode... allowed) {
        for (RoleCode candidate : allowed) {
            if (role == candidate) {
                return true;
            }
        }
        return false;
    }

    void touch(Instant now) {
        this.lastAccessedAt = now;
    }

    /**
     * Whether the idle deadline has passed.
     *
     * <p>The deadline is treated as reached, not merely passed, so a zero timeout
     * expires a session immediately rather than never. At a 30-minute timeout the
     * distinction is immaterial; at zero it is the difference between the setting
     * working and being silently ignored.
     */
    boolean isIdleBeyond(Duration timeout, Instant now) {
        return !lastAccessedAt.plus(timeout).isAfter(now);
    }

    /** The landing page for this session's role. */
    public String dashboardPath() {
        return switch (role) {
            case ADMIN -> "/admin/dashboard";
            case DENTIST -> "/dentist/dashboard";
            case PATIENT -> "/patient/dashboard";
        };
    }

    /** Deliberately omits the session id and CSRF token — this reaches log files. */
    @Override
    public String toString() {
        return "Session[" + username + ", " + role + "]";
    }
}
