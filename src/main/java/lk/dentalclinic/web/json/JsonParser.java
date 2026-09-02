package lk.icbt.dentalclinic.web.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A recursive-descent JSON parser, per RFC 8259.
 *
 * <p>The reading half of the Jackson substitute. Roughly 180 lines for a complete
 * grammar, which is a fair illustration of how small JSON actually is once the streaming,
 * data binding and configuration are left out.
 *
 * <p><strong>Hardened for untrusted input</strong>, because a request body is exactly
 * that:
 *
 * <ul>
 *   <li><strong>Nesting is capped.</strong> {@code [[[[[…]]]]]} nested a few thousand deep
 *       would otherwise overflow the stack — a denial of service costing the attacker one
 *       small request. Depth beyond {@value #MAX_DEPTH} is refused.</li>
 *   <li><strong>Trailing content is rejected.</strong> {@code {"a":1} garbage} is not
 *       valid JSON, and quietly ignoring the remainder is how a parser ends up
 *       disagreeing with the client about what was sent.</li>
 *   <li><strong>Every failure names its position</strong>, so an API client debugging a
 *       malformed body has something to work with.</li>
 * </ul>
 *
 * <p>Numbers are parsed as {@link BigDecimal}: money passes through this parser, and
 * {@code double} cannot represent 0.10 exactly.
 */
final class JsonParser {

    /** Deep enough for any legitimate request, shallow enough to be safe. */
    private static final int MAX_DEPTH = 64;

    private final String text;
    private int position;
    private int depth;

    private JsonParser(String text) {
        this.text = text;
    }

    static JsonObject parseObject(String text) {
        if (text == null || text.isBlank()) {
            throw new JsonException("The request body is empty; expected a JSON object");
        }
        JsonParser parser = new JsonParser(text);
        parser.skipWhitespace();
        Object value = parser.parseValue();
        parser.skipWhitespace();

        if (parser.position < text.length()) {
            throw new JsonException("Unexpected content after the JSON value at position "
                    + parser.position);
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new JsonException("Expected a JSON object but found "
                    + describe(value));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) map;
        return new JsonObject(typed);
    }

    // ------------------------------------------------------------------ grammar

    private Object parseValue() {
        skipWhitespace();
        if (position >= text.length()) {
            throw new JsonException("Unexpected end of input at position " + position);
        }

        char c = text.charAt(position);
        return switch (c) {
            case '{' -> parseObjectBody();
            case '[' -> parseArrayBody();
            case '"' -> parseString();
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default -> parseNumber();
        };
    }

    private Map<String, Object> parseObjectBody() {
        enter();
        Map<String, Object> result = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();

        if (peek() == '}') {
            position++;
            leave();
            return result;
        }

        while (true) {
            skipWhitespace();
            String name = parseString();
            skipWhitespace();
            expect(':');
            result.put(name, parseValue());
            skipWhitespace();

            char next = peek();
            if (next == ',') {
                position++;
            } else if (next == '}') {
                position++;
                leave();
                return result;
            } else {
                throw new JsonException("Expected ',' or '}' at position " + position
                        + " but found '" + next + "'");
            }
        }
    }

    private List<Object> parseArrayBody() {
        enter();
        List<Object> result = new ArrayList<>();
        expect('[');
        skipWhitespace();

        if (peek() == ']') {
            position++;
            leave();
            return result;
        }

        while (true) {
            result.add(parseValue());
            skipWhitespace();

            char next = peek();
            if (next == ',') {
                position++;
            } else if (next == ']') {
                position++;
                leave();
                return result;
            } else {
                throw new JsonException("Expected ',' or ']' at position " + position
                        + " but found '" + next + "'");
            }
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder out = new StringBuilder();

        while (true) {
            if (position >= text.length()) {
                throw new JsonException("Unterminated string starting before position " + position);
            }
            char c = text.charAt(position++);

            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }

            if (position >= text.length()) {
                throw new JsonException("Unterminated escape at position " + position);
            }
            char escape = text.charAt(position++);
            switch (escape) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> out.append(parseUnicodeEscape());
                default -> throw new JsonException(
                        "Invalid escape '\\" + escape + "' at position " + (position - 1));
            }
        }
    }

    private char parseUnicodeEscape() {
        if (position + 4 > text.length()) {
            throw new JsonException("Truncated \\u escape at position " + position);
        }
        String hex = text.substring(position, position + 4);
        position += 4;
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw new JsonException("Invalid \\u escape '" + hex + "' at position "
                    + (position - 4));
        }
    }

    private Boolean parseBoolean() {
        if (text.startsWith("true", position)) {
            position += 4;
            return Boolean.TRUE;
        }
        if (text.startsWith("false", position)) {
            position += 5;
            return Boolean.FALSE;
        }
        throw new JsonException("Invalid literal at position " + position);
    }

    private Object parseNull() {
        if (text.startsWith("null", position)) {
            position += 4;
            return null;
        }
        throw new JsonException("Invalid literal at position " + position);
    }

    private BigDecimal parseNumber() {
        int start = position;
        if (peek() == '-') {
            position++;
        }
        while (position < text.length() && isNumberChar(text.charAt(position))) {
            position++;
        }
        String raw = text.substring(start, position);
        if (raw.isEmpty()) {
            throw new JsonException("Expected a value at position " + start
                    + " but found '" + peek() + "'");
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new JsonException("Invalid number '" + raw + "' at position " + start);
        }
    }

    private static boolean isNumberChar(char c) {
        return (c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E'
                || c == '+' || c == '-';
    }

    // ------------------------------------------------------------------ helpers

    private void enter() {
        if (++depth > MAX_DEPTH) {
            throw new JsonException("JSON nested deeper than " + MAX_DEPTH + " levels");
        }
    }

    private void leave() {
        depth--;
    }

    private char peek() {
        if (position >= text.length()) {
            throw new JsonException("Unexpected end of input at position " + position);
        }
        return text.charAt(position);
    }

    private void expect(char expected) {
        if (position >= text.length() || text.charAt(position) != expected) {
            throw new JsonException("Expected '" + expected + "' at position " + position);
        }
        position++;
    }

    private void skipWhitespace() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }

    private static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof List) {
            return "an array";
        }
        return "a " + value.getClass().getSimpleName().toLowerCase();
    }
}
