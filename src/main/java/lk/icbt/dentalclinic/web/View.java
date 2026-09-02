package lk.icbt.dentalclinic.web;

import com.sun.net.httpserver.HttpExchange;
import lk.icbt.dentalclinic.security.Session;

import java.io.IOException;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders a template with the values every page needs, so no handler has to remember
 * to supply the navigation state or the CSRF token.
 *
 * <p>Forgetting the token would be the dangerous omission: the form would render, the
 * user would fill it in, and the POST would be rejected — or worse, if the check were
 * lenient, accepted without one. Injecting it centrally removes the chance.
 */
public final class View {

    private final TemplateEngine engine;

    public View(TemplateEngine engine) {
        this.engine = engine;
    }

    /** Starts a model already populated with the session-dependent values. */
    public Map<String, Object> model(HttpExchange exchange) {
        Map<String, Object> model = new HashMap<>();
        model.put("year", String.valueOf(Year.now().getValue()));

        Session session = WebContext.session().orElse(null);
        model.put("signedIn", session != null);
        if (session != null) {
            model.put("username", session.getUsername());
            model.put("fullName", session.getFullName());
            model.put("role", session.getRole().name());
            model.put("roleLabel", roleLabel(session));
            model.put("dashboardPath", session.dashboardPath());
            model.put("csrf", session.getCsrfToken());
            model.put("isAdmin", session.getRole().name().equals("ADMIN"));
            model.put("isDentist", session.getRole().name().equals("DENTIST"));
            model.put("isPatient", session.getRole().name().equals("PATIENT"));
        }
        return model;
    }

    public void render(HttpExchange exchange, String template, Map<String, Object> model)
            throws IOException {
        render(exchange, 200, template, model);
    }

    public void render(HttpExchange exchange, int status, String template,
                       Map<String, Object> model) throws IOException {
        Responses.html(exchange, status, engine.render(template, model));
    }

    private static String roleLabel(Session session) {
        return switch (session.getRole()) {
            case ADMIN -> "Administrator";
            case DENTIST -> "Dentist";
            case PATIENT -> "Patient";
        };
    }
}
