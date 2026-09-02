package lk.icbt.dentalclinic.service;

import lk.icbt.dentalclinic.dao.InMemoryUserDao;
import lk.icbt.dentalclinic.model.User;
import lk.icbt.dentalclinic.security.PasswordHasher;
import lk.icbt.dentalclinic.security.Session;
import lk.icbt.dentalclinic.security.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers every branch of the Login sequence diagram, with no database.
 *
 * <p>Uses a real {@link PasswordHasher} rather than a stub: password verification is
 * the thing under test, and a stub that always returns true would let a real bug
 * through while the test still passed.
 */
class AuthServiceTest {

    private static final String PASSWORD = "Admin@12345";

    private InMemoryUserDao users;
    private PasswordHasher hasher;
    private SessionManager sessions;
    private AuthService authService;
    private User admin;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserDao();
        hasher = new PasswordHasher();
        sessions = SessionManager.getInstance();
        authService = new AuthService(users, hasher, sessions);
        admin = users.add("admin", hasher.hash(PASSWORD), InMemoryUserDao.ADMIN_ROLE);
    }

    private AuthResult signIn(String username, String password) {
        return authService.authenticate(username, password.toCharArray(), null);
    }

    @Nested
    @DisplayName("successful sign-in")
    class Success {

        @Test
        @DisplayName("returns a session carrying the user's identity and role")
        void createsSession() {
            AuthResult result = signIn("admin", PASSWORD);

            assertTrue(result.isSuccess());
            Session session = result.sessionIfSuccessful().orElseThrow();
            assertEquals("admin", session.getUsername());
            assertEquals(InMemoryUserDao.ADMIN_ROLE.getCode(), session.getRole());
            assertEquals("/admin/dashboard", session.dashboardPath());
        }

        @Test
        @DisplayName("the username is matched case-insensitively")
        void usernameIsCaseInsensitive() {
            assertTrue(signIn("ADMIN", PASSWORD).isSuccess());
        }

        @Test
        @DisplayName("clears the failed-attempt counter")
        void clearsFailedAttempts() {
            signIn("admin", "wrong-password");
            assertEquals(1, users.get(admin.getUserId()).getFailedLoginAttempts());

            signIn("admin", PASSWORD);

            assertEquals(0, users.get(admin.getUserId()).getFailedLoginAttempts());
        }

        @Test
        @DisplayName("invalidates the identifier the browser arrived with (session fixation)")
        void rotatesSessionId() {
            // An attacker plants a session id in the victim's browser before sign-in.
            Session planted = sessions.createFor(admin);
            String plantedId = planted.getId();

            AuthResult result =
                    authService.authenticate("admin", PASSWORD.toCharArray(), plantedId);

            Session issued = result.sessionIfSuccessful().orElseThrow();
            assertNotEquals(plantedId, issued.getId(), "a new identifier must be issued");
            assertTrue(sessions.find(plantedId).isEmpty(),
                    "the planted identifier must no longer work");
            assertTrue(sessions.find(issued.getId()).isPresent());
        }

        @Test
        @DisplayName("a hash below the current cost is transparently upgraded on sign-in")
        void rehashesWeakStoredHash() {
            // A valid hash from when the iteration count was lower.
            String weak = hasher.hash(PASSWORD.toCharArray(), 120_000);
            users.updatePasswordHash(admin.getUserId(), weak);
            users.passwordUpdates.clear();

            assertTrue(signIn("admin", PASSWORD).isSuccess(),
                    "the old hash must still verify, or users would be locked out by an upgrade");

            assertEquals(1, users.passwordUpdates.size(), "the hash should have been rewritten");
            assertTrue(users.passwordUpdates.get(0).startsWith(
                            "pbkdf2-sha256$" + PasswordHasher.ITERATIONS + "$"),
                    "the replacement must be at the current cost");
        }

        @Test
        @DisplayName("a hash already at the current cost is left alone")
        void doesNotRehashCurrentCost() {
            users.passwordUpdates.clear();

            assertTrue(signIn("admin", PASSWORD).isSuccess());

            assertTrue(users.passwordUpdates.isEmpty(),
                    "re-hashing on every sign-in would waste 100ms per request");
        }
    }

    @Nested
    @DisplayName("refused sign-in")
    class Refused {

        @Test
        @DisplayName("an unknown username gives the same message as a wrong password")
        void doesNotRevealWhetherAccountExists() {
            AuthResult unknown = signIn("no-such-person", PASSWORD);
            AuthResult wrongPassword = signIn("admin", "not-the-password");

            assertEquals(AuthResult.Outcome.INVALID_CREDENTIALS, unknown.outcome());
            assertEquals(AuthResult.Outcome.INVALID_CREDENTIALS, wrongPassword.outcome());
            assertEquals(unknown.userMessage(), wrongPassword.userMessage(),
                    "identical messages are what stop account enumeration");
        }

        @Test
        @DisplayName("a blank username is refused without touching the database")
        void blankUsernameRefused() {
            assertFalse(signIn("", PASSWORD).isSuccess());
            assertFalse(signIn("   ", PASSWORD).isSuccess());
        }

        @Test
        @DisplayName("a disabled account is refused, and indistinguishably so")
        void disabledAccountRefused() {
            users.replace(User.builder()
                    .userId(admin.getUserId())
                    .username(admin.getUsername())
                    .fullName(admin.getFullName())
                    .role(admin.getRole())
                    .active(false)
                    .build());

            AuthResult result = signIn("admin", PASSWORD);

            assertEquals(AuthResult.Outcome.INVALID_CREDENTIALS, result.outcome());
        }
    }

    @Nested
    @DisplayName("lock-out")
    class Lockout {

        @Test
        @DisplayName("locks the account on the fifth consecutive failure, not the fourth")
        void locksOnFifthAttempt() {
            for (int attempt = 1; attempt < AuthService.MAX_FAILED_ATTEMPTS; attempt++) {
                assertEquals(AuthResult.Outcome.INVALID_CREDENTIALS,
                        signIn("admin", "wrong").outcome(),
                        "attempt " + attempt + " should not lock the account yet");
            }

            AuthResult fifth = signIn("admin", "wrong");

            assertEquals(AuthResult.Outcome.LOCKED, fifth.outcome());
            assertTrue(fifth.lockedUntil().isAfter(LocalDateTime.now()));
        }

        @Test
        @DisplayName("the correct password is refused while the lock is in force")
        void correctPasswordRefusedWhileLocked() {
            users.lockUntil(admin.getUserId(), LocalDateTime.now().plusMinutes(15));

            AuthResult result = signIn("admin", PASSWORD);

            assertEquals(AuthResult.Outcome.LOCKED, result.outcome());
        }

        @Test
        @DisplayName("an expired lock no longer blocks sign-in")
        void expiredLockIsIgnored() {
            users.lockUntil(admin.getUserId(), LocalDateTime.now().minusMinutes(1));

            assertTrue(signIn("admin", PASSWORD).isSuccess());
        }
    }

    @Test
    @DisplayName("signing out invalidates the session and is safe to repeat")
    void signOutIsIdempotent() {
        Session session = signIn("admin", PASSWORD).sessionIfSuccessful().orElseThrow();

        authService.signOut(session.getId());
        assertTrue(sessions.find(session.getId()).isEmpty());

        authService.signOut(session.getId());
        authService.signOut(null);
    }
}
