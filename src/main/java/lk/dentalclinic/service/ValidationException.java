package lk.dentalclinic.service;

import lk.dentalclinic.validation.ValidationResult;

/**
 * A submission failed one or more field rules.
 *
 * <p>Carries the whole {@link ValidationResult} rather than a single message, so the
 * handler can redisplay the form with every error beside its own field instead of one
 * error at the top of the page.
 */
public class ValidationException extends RuntimeException {

    private final transient ValidationResult result;

    public ValidationException(ValidationResult result) {
        super(result.firstError().orElse("The submission is not valid."));
        this.result = result;
    }

    public ValidationResult result() {
        return result;
    }
}
