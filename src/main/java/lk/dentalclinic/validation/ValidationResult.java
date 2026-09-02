package lk.dentalclinic.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The accumulated errors from validating one submission, keyed by field name.
 *
 * <p><strong>Collects rather than throws.</strong> Failing on the first bad field would
 * make a user fix a form one error per round trip. Gathering them all means the page
 * can be redisplayed with every problem marked at once, which is what
 * {@code "appropriate messages"} in the brief asks for.
 *
 * <p>Insertion-ordered, so errors appear in the order the fields appear on the form.
 */
public final class ValidationResult {

    private final Map<String, String> errors = new LinkedHashMap<>();

    public static ValidationResult empty() {
        return new ValidationResult();
    }

    /** Records an error, keeping the first message for a field. */
    public ValidationResult reject(String field, String message) {
        errors.putIfAbsent(field, message);
        return this;
    }

    /** Records an error only when the condition holds. */
    public ValidationResult rejectIf(boolean condition, String field, String message) {
        if (condition) {
            reject(field, message);
        }
        return this;
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public Map<String, String> errors() {
        return Collections.unmodifiableMap(errors);
    }

    public Optional<String> errorFor(String field) {
        return Optional.ofNullable(errors.get(field));
    }

    /** The first message, for contexts with room for only one line. */
    public Optional<String> firstError() {
        return errors.values().stream().findFirst();
    }

    public ValidationResult merge(ValidationResult other) {
        other.errors.forEach(this::reject);
        return this;
    }

    @Override
    public String toString() {
        return errors.isEmpty() ? "valid" : errors.toString();
    }
}
