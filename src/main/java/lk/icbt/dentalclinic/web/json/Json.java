package lk.icbt.dentalclinic.web.json;

import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A hand-written JSON writer — the substitute for Jackson.
 *
 * <p>BUILDER pattern. The alternative for producing an API response is string
 * concatenation, which gets the escaping wrong the first time somebody's address contains
 * a quotation mark, and the comma placement wrong the first time a field is conditional.
 * A builder makes both impossible: separators are the builder's business, and every value
 * goes through {@link #escape}.
 *
 * <pre>
 * Json.object()
 *     .put("appointmentNo", "APT-2026-0001")
 *     .put("date", LocalDate.now())
 *     .putObject("patient", p -&gt; p.put("name", patient.getFullName()))
 *     .putArray("items", a -&gt; items.forEach(i -&gt; a.addObject(o -&gt; o.put("x", i))))
 *     .toJson();
 * </pre>
 *
 * <p><strong>Scope, stated honestly.</strong> This writes JSON; it does not map objects
 * to it. Jackson would serialise an entity by reflection with no code at all, and would
 * handle streaming, polymorphism and a hundred configuration options. Writing each
 * response explicitly costs a few lines per endpoint — but it also means the API's shape
 * is chosen deliberately rather than falling out of whatever fields an entity happens to
 * have, which is exactly the leak the DTO pattern exists to prevent. At this size that is
 * a fair trade; at a hundred endpoints it would not be.
 *
 * <p>Temporal values are written as ISO-8601 strings, which is what
 * {@code LocalDate.toString()} already produces and what every JSON client expects.
 */
public final class Json {

    private Json() {
    }

    public static JsonObjectBuilder object() {
        return new JsonObjectBuilder();
    }

    public static JsonArrayBuilder array() {
        return new JsonArrayBuilder();
    }

    /** Parses JSON text into an object view. */
    public static JsonObject parseObject(String text) {
        return JsonParser.parseObject(text);
    }

    // ------------------------------------------------------------------ writer

    /** Builds a JSON object. Not thread-safe; one per response. */
    public static final class JsonObjectBuilder {

        private final Map<String, String> fields = new LinkedHashMap<>();

        private JsonObjectBuilder() {
        }

        public JsonObjectBuilder put(String name, String value) {
            fields.put(name, value == null ? "null" : quote(value));
            return this;
        }

        public JsonObjectBuilder put(String name, Number value) {
            // Written unquoted, so a client reads a number rather than a string. Money is
            // BigDecimal, whose toPlainString avoids the exponent form that some JSON
            // parsers mishandle.
            fields.put(name, value == null ? "null"
                    : value instanceof BigDecimal decimal
                            ? decimal.toPlainString() : value.toString());
            return this;
        }

        public JsonObjectBuilder put(String name, boolean value) {
            fields.put(name, Boolean.toString(value));
            return this;
        }

        public JsonObjectBuilder put(String name, Temporal value) {
            fields.put(name, value == null ? "null" : quote(value.toString()));
            return this;
        }

        public JsonObjectBuilder put(String name, Enum<?> value) {
            fields.put(name, value == null ? "null" : quote(value.name()));
            return this;
        }

        /** Omits the field entirely when the value is absent, rather than writing null. */
        public JsonObjectBuilder putIfPresent(String name, String value) {
            if (value != null && !value.isEmpty()) {
                put(name, value);
            }
            return this;
        }

        public JsonObjectBuilder putObject(String name, Consumer<JsonObjectBuilder> build) {
            JsonObjectBuilder nested = new JsonObjectBuilder();
            build.accept(nested);
            fields.put(name, nested.toJson());
            return this;
        }

        public JsonObjectBuilder putArray(String name, Consumer<JsonArrayBuilder> build) {
            JsonArrayBuilder nested = new JsonArrayBuilder();
            build.accept(nested);
            fields.put(name, nested.toJson());
            return this;
        }

        /** Embeds JSON produced elsewhere. The caller is responsible for its validity. */
        public JsonObjectBuilder putRaw(String name, String json) {
            fields.put(name, json == null ? "null" : json);
            return this;
        }

        public String toJson() {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> field : fields.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                out.append(quote(field.getKey())).append(':').append(field.getValue());
                first = false;
            }
            return out.append('}').toString();
        }

        @Override
        public String toString() {
            return toJson();
        }
    }

    /** Builds a JSON array. */
    public static final class JsonArrayBuilder {

        private final List<String> elements = new ArrayList<>();

        private JsonArrayBuilder() {
        }

        public JsonArrayBuilder add(String value) {
            elements.add(value == null ? "null" : quote(value));
            return this;
        }

        public JsonArrayBuilder add(Number value) {
            elements.add(value == null ? "null"
                    : value instanceof BigDecimal decimal
                            ? decimal.toPlainString() : value.toString());
            return this;
        }

        public JsonArrayBuilder addObject(Consumer<JsonObjectBuilder> build) {
            JsonObjectBuilder nested = new JsonObjectBuilder();
            build.accept(nested);
            elements.add(nested.toJson());
            return this;
        }

        /** Maps a collection straight into the array — the common case for a list endpoint. */
        public <T> JsonArrayBuilder addAll(Iterable<T> items,
                                           java.util.function.BiConsumer<JsonObjectBuilder, T> build) {
            for (T item : items) {
                addObject(builder -> build.accept(builder, item));
            }
            return this;
        }

        public String toJson() {
            return "[" + String.join(",", elements) + "]";
        }

        @Override
        public String toString() {
            return toJson();
        }
    }

    // ------------------------------------------------------------------ escaping

    static String quote(String raw) {
        return '"' + escape(raw) + '"';
    }

    /**
     * Escapes a string for JSON, per RFC 8259.
     *
     * <p>The five named escapes, plus {@code \"} and {@code \\}, plus a {@code \\uXXXX}
     * form for every other control character. Getting this wrong is not a formatting
     * nicety: an unescaped quotation mark in a patient's address ends the string early and
     * produces a response the client cannot parse, and an unescaped control character
     * produces JSON that is invalid without looking it.
     *
     * <p>{@code U+2028} and {@code U+2029} are escaped too. They are valid in JSON but are
     * line terminators in JavaScript, so a response embedded in a script tag would break —
     * a well-known and easily missed defect.
     */
    static String escape(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    // U+2028 and U+2029 are legal in JSON but are line terminators in
                    // JavaScript, so a response embedded in a script tag would break.
                    // Tested by hex value rather than as a char literal: writing
                    // ' ' in Java source is rewritten by the Unicode preprocessor
                    // *before* lexing, which puts a real line break inside the literal
                    // and fails to compile.
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
