package lk.dentalclinic.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateEngineTest {

    private final TemplateEngine engine = new TemplateEngine();

    private static Map<String, Object> model(Object... pairs) {
        Map<String, Object> model = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            model.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return model;
    }

    @Test
    @DisplayName("substituted values are HTML-escaped by default")
    void escapesByDefault() {
        String rendered = engine.render("login",
                model("username", "<script>alert('xss')</script>"));

        assertFalse(rendered.contains("<script>alert"),
                "an unescaped value here would be stored XSS");
        assertTrue(rendered.contains("&lt;script&gt;"));
    }

    @Test
    @DisplayName("the triple-brace form inserts raw HTML")
    void rawSubstitutionIsNotEscaped() {
        String rendered = engine.render("help",
                model("topics", "<article class=\"card\">Injected</article>"));

        assertTrue(rendered.contains("<article class=\"card\">Injected</article>"),
                "handlers pre-render list markup and pass it raw");
    }

    @Test
    @DisplayName("{{#if}} keeps the block only for a truthy value")
    void ifBlockRespectsTruthiness() {
        String withError = engine.render("login", model("error", "Sign-in details are incorrect."));
        assertTrue(withError.contains("Sign-in details are incorrect."));

        String withoutError = engine.render("login", model());
        assertFalse(withoutError.contains("alert error"));
    }

    @Test
    @DisplayName("{{#unless}} is the inverse of {{#if}}")
    void unlessBlockIsInverted() {
        String anonymous = engine.render("login", model("signedIn", false));
        assertTrue(anonymous.contains("Sign in"));

        String signedIn = engine.render("login", model("signedIn", true, "fullName", "Kasun"));
        assertTrue(signedIn.contains("Sign out"));
    }

    @Test
    @DisplayName("empty strings and empty collections are falsy")
    void emptyValuesAreFalsy() {
        assertFalse(engine.render("login", model("error", "")).contains("alert error"));
        assertFalse(engine.render("login", model("error", List.of())).contains("alert error"));
    }

    @Test
    @DisplayName("{{>partial}} includes another template")
    void resolvesIncludes() {
        String rendered = engine.render("login", model());

        assertTrue(rendered.startsWith("<!doctype html>"), "header.html should be included");
        assertTrue(rendered.contains("</html>"), "footer.html should be included");
    }

    @Test
    @DisplayName("an absent key renders as empty rather than throwing")
    void missingKeyRendersEmpty() {
        String rendered = engine.render("login", model());

        assertFalse(rendered.contains("{{"), "no placeholder should survive rendering");
    }

    @Test
    @DisplayName("a missing template fails loudly, naming the file")
    void missingTemplateThrows() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> engine.render("no-such-template", model()));

        assertTrue(thrown.getMessage().contains("no-such-template"));
    }

    @Test
    @DisplayName("every shipped template renders without leaving a placeholder behind")
    void allTemplatesRender() {
        // A cheap guard against a typo in a template name or an unclosed {{#if}},
        // which would otherwise only surface when a user opened that page.
        List<String> templates = List.of("login", "register", "help",
                "dashboard-admin", "dashboard-dentist", "dashboard-patient",
                "appointments", "appointment-new", "appointment-detail",
                "appointment-search", "availability",
                "records-patients", "records-dentists", "records-treatments",
                "bills", "bill-new", "bill-detail", "receipt",
                "reports", "visit-history");

        for (String template : templates) {
            String rendered = engine.render(template, model("signedIn", true,
                    "fullName", "Test User", "roleLabel", "Administrator",
                    "dashboardPath", "/admin/dashboard", "csrf", "token"));
            assertFalse(rendered.contains("{{"),
                    template + ".html left an unresolved placeholder");
            assertTrue(rendered.contains("</html>"), template + ".html did not close");
        }
    }

    @Test
    @DisplayName("attribute-breaking quotes are escaped, not only angle brackets")
    void escapesQuotes() {
        String rendered = engine.render("login", model("username", "\" onfocus=\"alert(1)"));

        assertFalse(rendered.contains("onfocus=\"alert(1)"),
                "an unescaped quote would end the value attribute and start a new one");
        // Both quotes in the payload must be escaped, not just the first.
        assertEquals(2, countOccurrences(rendered, "&quot;"), rendered);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
