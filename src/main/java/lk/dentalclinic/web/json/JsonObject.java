package lk.dentalclinic.web.json;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A read-only view over a parsed JSON object.
 *
 * <p>Every accessor is total: a missing field, a null, or a value of the wrong type all
 * yield {@link Optional#empty()} rather than an exception. That is deliberate. A request
 * body arrives from outside the system and may be anything at all; treating a malformed
 * field as a validation failure the handler reports, rather than as a crash, is the
 * difference between a 400 with a useful message and a 500 with a stack trace.
 *
 * <p>The {@code require*} variants throw {@link JsonException} for the cases where a
 * missing field genuinely means the request is unusable.
 */
public final class JsonObject {

    private final Map<String, Object> values;

    JsonObject(Map<String, Object> values) {
        this.values = values;
    }

    public boolean has(String name) {
        return values.get(name) != null;
    }

    public Optional<String> getString(String name) {
        Object value = values.get(name);
        if (value == null) {
            return Optional.empty();
        }
        // A number or boolean is accepted where a string is asked for: a client sending
        // {"quantity": 2} rather than {"quantity": "2"} is being reasonable, not wrong.
        return Optional.of(value instanceof String text ? text : String.valueOf(value));
    }

    public String requireString(String name) {
        return getString(name).filter(s -> !s.isBlank())
                .orElseThrow(() -> new JsonException("Field '" + name + "' is required"));
    }

    public Optional<Integer> getInt(String name) {
        return getNumber(name).map(BigDecimal::intValue);
    }

    public int requireInt(String name) {
        return getInt(name).orElseThrow(
                () -> new JsonException("Field '" + name + "' must be a whole number"));
    }

    public Optional<BigDecimal> getNumber(String name) {
        Object value = values.get(name);
        if (value instanceof BigDecimal number) {
            return Optional.of(number);
        }
        if (value instanceof String text) {
            try {
                return Optional.of(new BigDecimal(text.trim()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public Optional<Boolean> getBoolean(String name) {
        Object value = values.get(name);
        if (value instanceof Boolean flag) {
            return Optional.of(flag);
        }
        if (value instanceof String text) {
            return Optional.of(Boolean.parseBoolean(text));
        }
        return Optional.empty();
    }

    /** ISO-8601 only — the format {@link Json} writes, so the API round-trips. */
    public Optional<LocalDate> getDate(String name) {
        return getString(name).flatMap(text -> {
            try {
                return Optional.of(LocalDate.parse(text.trim()));
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        });
    }

    /** Accepts both {@code HH:mm} and {@code HH:mm:ss}. */
    public Optional<LocalTime> getTime(String name) {
        return getString(name).flatMap(text -> {
            try {
                String value = text.trim();
                return Optional.of(LocalTime.parse(value.length() == 5 ? value + ":00" : value));
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        });
    }

    public Optional<JsonObject> getObject(String name) {
        Object value = values.get(name);
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return Optional.of(new JsonObject(typed));
        }
        return Optional.empty();
    }

    public List<Object> getArray(String name) {
        Object value = values.get(name);
        return value instanceof List<?> list ? List.copyOf(list) : List.of();
    }

    public java.util.Set<String> fieldNames() {
        return values.keySet();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public String toString() {
        return "JsonObject" + values.keySet();
    }
}
