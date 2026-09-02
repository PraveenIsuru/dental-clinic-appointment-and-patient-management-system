package lk.dentalclinic.web.handler;

import com.sun.net.httpserver.HttpExchange;
import lk.dentalclinic.service.RegistrationRequest;
import lk.dentalclinic.service.RegistrationService;
import lk.dentalclinic.web.Handler;
import lk.dentalclinic.web.Requests;
import lk.dentalclinic.web.Responses;
import lk.dentalclinic.web.View;
import lk.dentalclinic.web.WebContext;

import java.io.IOException;
import java.util.Map;

/**
 * Patient self-registration — {@code GET /register} and {@code POST /register}.
 *
 * <p>On failure the form is redisplayed with every field's error beside it and the
 * typed values preserved, apart from the passwords. Making the user retype a long form
 * because one field was wrong is how people end up choosing weaker passwords.
 */
public final class RegisterHandler implements Handler {

    private final RegistrationService registrationService;
    private final View view;

    public RegisterHandler(RegistrationService registrationService, View view) {
        this.registrationService = registrationService;
        this.view = view;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            submit(exchange);
        } else {
            showForm(exchange);
        }
    }

    private void showForm(HttpExchange exchange) throws IOException {
        if (WebContext.isSignedIn()) {
            Responses.redirect(exchange, WebContext.requireSession().dashboardPath());
            return;
        }
        view.render(exchange, "register", view.model(exchange));
    }

    private void submit(HttpExchange exchange) throws IOException {
        Map<String, String> form = Requests.form(exchange);

        RegistrationRequest request = new RegistrationRequest(
                Requests.field(form, "fullName"),
                Requests.field(form, "username"),
                Requests.field(form, "password").toCharArray(),
                Requests.field(form, "confirmPassword").toCharArray(),
                Requests.field(form, "email"),
                Requests.field(form, "contactNumber"),
                Requests.field(form, "address"));

        RegistrationService.Outcome outcome = registrationService.register(request);

        if (outcome.isSuccess()) {
            // Redirect rather than render: a refresh on the confirmation page must not
            // resubmit the registration (the POST/redirect/GET pattern).
            Responses.redirect(exchange, "/login?registered="
                    + outcome.patient().getPatientNo());
            return;
        }

        Map<String, Object> model = view.model(exchange);
        model.put("fullName", form.get("fullName"));
        model.put("username", form.get("username"));
        model.put("email", form.get("email"));
        model.put("contactNumber", form.get("contactNumber"));
        model.put("address", form.get("address"));

        outcome.validation().errors().forEach((field, message) ->
                model.put("error_" + field, message));
        model.put("hasErrors", true);

        view.render(exchange, 422, "register", model);
    }
}
