package lk.dentalclinic.security;

import lk.dentalclinic.model.RoleCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * URL-level authorisation rules, evaluated in declaration order with the first match
 * winning.
 *
 * <p>This is the "belt" half of belt-and-braces: handlers additionally check what the
 * signed-in user may touch at the level of individual records (a patient may read
 * <em>their own</em> appointment, not merely "an appointment"), which no URL pattern
 * can express. Having both means forgetting one is not immediately a breach.
 *
 * <p><strong>Deny by default.</strong> Anything not matched by a rule requires
 * authentication. A new page is therefore private until someone deliberately opens it,
 * which is the safe direction for the mistake to fall.
 */
public final class AccessRules {

    /** What a rule demands of the caller. */
    private enum Requirement { PUBLIC, AUTHENTICATED, ROLES }

    private record Rule(String prefix, Requirement requirement, Set<RoleCode> roles) {

        /**
         * Matches the prefix itself or a path below it, on segment boundaries.
         *
         * <p>The root is special-cased to an exact match. Treating "/" as a prefix
         * would make {@code permitAll("/")} match every path in the application —
         * since every path starts with "/" — and because rules are evaluated in
         * declaration order and the root is declared first, that single rule would
         * silently make the entire system public. Caught by
         * {@code AccessRulesTest.protectedPathsRefuseAnonymous}.
         *
         * <p>The trailing slash on the prefix is what keeps "/admin" from matching
         * "/administration-secrets".
         */
        boolean matches(String path) {
            if ("/".equals(prefix)) {
                return "/".equals(path);
            }
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
    }

    private final List<Rule> rules = new ArrayList<>();

    public static AccessRules defaults() {
        return new AccessRules()
                // --- open to anyone, signed in or not -------------------------
                .permitAll("/")
                .permitAll("/index.html")
                .permitAll("/login")
                .permitAll("/logout")
                .permitAll("/register")
                .permitAll("/help")
                .permitAll("/health")
                .permitAll("/css")
                .permitAll("/js")
                .permitAll("/favicon.ico")
                // The API reference and the Postman collection are documentation, not
                // data: readable without signing in, exactly as Swagger UI would be.
                .permitAll("/api-docs")
                .permitAll("/api-docs.html")
                .permitAll("/postman-collection.json")
                // --- role-scoped areas ----------------------------------------
                .require("/admin", RoleCode.ADMIN)
                .require("/dentist", RoleCode.DENTIST)
                .require("/patient", RoleCode.PATIENT)
                // Shared pages; the handler narrows further per record (A6).
                .authenticated("/appointments")
                .authenticated("/bills")
                .authenticated("/account")
                // Every API endpoint requires a session. Individual handlers narrow
                // further by role and by record (A6).
                .authenticated("/api");
    }

    public AccessRules permitAll(String prefix) {
        rules.add(new Rule(prefix, Requirement.PUBLIC, Set.of()));
        return this;
    }

    public AccessRules authenticated(String prefix) {
        rules.add(new Rule(prefix, Requirement.AUTHENTICATED, Set.of()));
        return this;
    }

    public AccessRules require(String prefix, RoleCode... roles) {
        rules.add(new Rule(prefix, Requirement.ROLES, Set.of(roles)));
        return this;
    }

    public boolean isPublic(String path) {
        Rule rule = firstMatch(path);
        return rule != null && rule.requirement() == Requirement.PUBLIC;
    }

    /** Whether a session — possibly none — may reach this path. */
    public boolean isAllowed(String path, Session session) {
        Rule rule = firstMatch(path);
        if (rule == null) {
            return session != null;   // deny by default
        }
        return switch (rule.requirement()) {
            case PUBLIC -> true;
            case AUTHENTICATED -> session != null;
            case ROLES -> session != null && rule.roles().contains(session.getRole());
        };
    }

    private Rule firstMatch(String path) {
        for (Rule rule : rules) {
            if (rule.matches(path)) {
                return rule;
            }
        }
        return null;
    }
}
