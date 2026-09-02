package lk.icbt.dentalclinic.security;

import lk.icbt.dentalclinic.model.Role;
import lk.icbt.dentalclinic.model.RoleCode;
import lk.icbt.dentalclinic.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionManagerTest {

    private static User user(int id, String username, RoleCode role) {
        return User.builder()
                .userId(id)
                .username(username)
                .fullName(username + " Example")
                .role(new Role(role.ordinal() + 1, role, role.name()))
                .build();
    }

    private SessionManager manager() {
        return SessionManager.withTimeout(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("a new session is retrievable by its identifier")
    void createAndFind() {
        SessionManager manager = manager();
        Session session = manager.createFor(user(1, "admin", RoleCode.ADMIN));

        assertTrue(manager.find(session.getId()).isPresent());
        assertEquals("admin", manager.find(session.getId()).orElseThrow().getUsername());
    }

    @Test
    @DisplayName("identifiers are long, random and never repeat")
    void identifiersAreUnpredictable() {
        SessionManager manager = manager();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 500; i++) {
            String id = manager.createFor(user(i, "user" + i, RoleCode.PATIENT)).getId();
            assertTrue(seen.add(id), "session identifiers must never collide");
            // 32 random bytes, base64url without padding.
            assertEquals(43, id.length(), id);
        }
    }

    @Test
    @DisplayName("the CSRF token is separate from the session identifier")
    void csrfTokenIsDistinct() {
        Session session = manager().createFor(user(1, "admin", RoleCode.ADMIN));

        assertNotEquals(session.getId(), session.getCsrfToken(),
                "reusing the session id as the CSRF token would put it in the page body");
        assertFalse(session.getCsrfToken().isBlank());
    }

    @Test
    @DisplayName("an unknown or blank identifier yields nothing")
    void unknownIdentifierIsEmpty() {
        SessionManager manager = manager();

        assertTrue(manager.find("not-a-real-session").isEmpty());
        assertTrue(manager.find("").isEmpty());
        assertTrue(manager.find(null).isEmpty());
    }

    @Test
    @DisplayName("an idle session expires and cannot be found again")
    void idleSessionExpires() {
        SessionManager manager = SessionManager.withTimeout(Duration.ZERO);
        Session session = manager.createFor(user(1, "admin", RoleCode.ADMIN));

        assertTrue(manager.find(session.getId()).isEmpty(),
                "a zero idle timeout should expire the session immediately");
        assertEquals(0, manager.activeCount(), "the expired entry should be dropped, not retained");
    }

    @Test
    @DisplayName("activity refreshes the idle deadline")
    void accessRefreshesDeadline() throws InterruptedException {
        SessionManager manager = SessionManager.withTimeout(Duration.ofMillis(200));
        Session session = manager.createFor(user(1, "admin", RoleCode.ADMIN));

        Thread.sleep(120);
        assertTrue(manager.find(session.getId()).isPresent(), "not yet idle");

        Thread.sleep(120);
        assertTrue(manager.find(session.getId()).isPresent(),
                "the previous lookup should have reset the clock");
    }

    @Test
    @DisplayName("signing out removes the session")
    void invalidateRemoves() {
        SessionManager manager = manager();
        Session session = manager.createFor(user(1, "admin", RoleCode.ADMIN));

        manager.invalidate(session.getId());

        assertTrue(manager.find(session.getId()).isEmpty());
        manager.invalidate(null);
    }

    @Test
    @DisplayName("disabling an account signs out every one of its sessions")
    void invalidateAllForUser() {
        SessionManager manager = manager();
        User admin = user(1, "admin", RoleCode.ADMIN);
        Session first = manager.createFor(admin);
        Session second = manager.createFor(admin);
        Session other = manager.createFor(user(2, "patient", RoleCode.PATIENT));

        int removed = manager.invalidateAllFor(1);

        assertEquals(2, removed);
        assertTrue(manager.find(first.getId()).isEmpty());
        assertTrue(manager.find(second.getId()).isEmpty());
        assertTrue(manager.find(other.getId()).isPresent(), "other users are unaffected");
    }

    @Test
    @DisplayName("purging drops expired entries so the map does not grow without bound")
    void purgeExpiredReclaims() {
        SessionManager manager = SessionManager.withTimeout(Duration.ZERO);
        for (int i = 0; i < 10; i++) {
            manager.createFor(user(i, "user" + i, RoleCode.PATIENT));
        }

        assertEquals(10, manager.purgeExpired());
        assertEquals(0, manager.activeCount());
    }

    @Test
    @DisplayName("the session knows the landing page for its role")
    void dashboardPathFollowsRole() {
        SessionManager manager = manager();

        assertEquals("/admin/dashboard",
                manager.createFor(user(1, "a", RoleCode.ADMIN)).dashboardPath());
        assertEquals("/dentist/dashboard",
                manager.createFor(user(2, "d", RoleCode.DENTIST)).dashboardPath());
        assertEquals("/patient/dashboard",
                manager.createFor(user(3, "p", RoleCode.PATIENT)).dashboardPath());
    }

    @Test
    @DisplayName("toString carries no session identifier or token")
    void toStringDoesNotLeakSecrets() {
        Session session = manager().createFor(user(1, "admin", RoleCode.ADMIN));

        String text = session.toString();

        assertFalse(text.contains(session.getId()), "log lines must not carry the session id");
        assertFalse(text.contains(session.getCsrfToken()));
        assertTrue(text.contains("admin"));
    }
}
