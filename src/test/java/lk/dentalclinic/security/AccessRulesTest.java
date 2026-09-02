package lk.dentalclinic.security;

import lk.dentalclinic.model.Role;
import lk.dentalclinic.model.RoleCode;
import lk.dentalclinic.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessRulesTest {

    private final AccessRules rules = AccessRules.defaults();
    private final SessionManager sessions = SessionManager.getInstance();

    private Session sessionFor(RoleCode code) {
        User user = User.builder()
                .userId(code.ordinal() + 1)
                .username(code.name().toLowerCase())
                .fullName("Test " + code)
                .role(new Role(code.ordinal() + 1, code, code.name()))
                .build();
        return sessions.createFor(user);
    }

    @ParameterizedTest
    @DisplayName("public pages are reachable without signing in")
    @ValueSource(strings = {"/", "/index.html", "/login", "/register", "/help",
            "/health", "/css/app.css"})
    void publicPathsAllowAnonymous(String path) {
        assertTrue(rules.isAllowed(path, null), path + " should be public");
    }

    @ParameterizedTest
    @DisplayName("role areas are closed to anonymous visitors")
    @ValueSource(strings = {"/admin/dashboard", "/dentist/dashboard", "/patient/dashboard",
            "/appointments", "/bills/BIL-2026-0001"})
    void protectedPathsRefuseAnonymous(String path) {
        assertFalse(rules.isAllowed(path, null), path + " should require a session");
    }

    @Test
    @DisplayName("each role reaches only its own area")
    void rolesAreSeparated() {
        Session admin = sessionFor(RoleCode.ADMIN);
        Session dentist = sessionFor(RoleCode.DENTIST);
        Session patient = sessionFor(RoleCode.PATIENT);

        assertTrue(rules.isAllowed("/admin/dashboard", admin));
        assertFalse(rules.isAllowed("/admin/dashboard", dentist));
        assertFalse(rules.isAllowed("/admin/dashboard", patient));

        assertTrue(rules.isAllowed("/dentist/dashboard", dentist));
        assertFalse(rules.isAllowed("/dentist/dashboard", admin));

        assertTrue(rules.isAllowed("/patient/dashboard", patient));
        assertFalse(rules.isAllowed("/patient/dashboard", admin));
    }

    @Test
    @DisplayName("shared areas admit any signed-in role")
    void sharedAreasAdmitEveryone() {
        for (RoleCode code : RoleCode.values()) {
            assertTrue(rules.isAllowed("/appointments", sessionFor(code)),
                    code + " should reach /appointments");
        }
    }

    @Test
    @DisplayName("an unmatched path is denied by default")
    void unknownPathsDenyByDefault() {
        // The safe direction for a mistake to fall: a new page is private until
        // someone deliberately opens it.
        assertFalse(rules.isAllowed("/some/page/added/later", null));
        assertTrue(rules.isAllowed("/some/page/added/later", sessionFor(RoleCode.ADMIN)));
    }

    @Test
    @DisplayName("a prefix rule does not leak to a path that merely starts with the same text")
    void prefixMatchingRespectsSegmentBoundaries() {
        // "/admin" must not match "/administration-secrets", so that path falls
        // through to the deny-by-default rule rather than to the ADMIN-only rule.
        assertFalse(rules.isPublic("/administration-secrets"));
        assertFalse(rules.isAllowed("/administration-secrets", null),
                "an unmatched path must still require a session");
        assertTrue(rules.isAllowed("/administration-secrets", sessionFor(RoleCode.DENTIST)),
                "it is unmatched, so deny-by-default applies - not the ADMIN-only rule");

        // "/css" likewise must not make "/cssoverride" public.
        assertFalse(rules.isPublic("/cssoverride"));

        // But a real segment below the prefix does match.
        assertTrue(rules.isPublic("/css/app.css"));
        assertTrue(rules.isAllowed("/admin/patients/new", sessionFor(RoleCode.ADMIN)));
        assertFalse(rules.isAllowed("/admin/patients/new", sessionFor(RoleCode.PATIENT)));
    }

    @Test
    @DisplayName("/login stays public so a signed-out user can get back in")
    void loginRemainsPublic() {
        assertTrue(rules.isPublic("/login"));
        assertTrue(rules.isPublic("/logout"));
    }
}
