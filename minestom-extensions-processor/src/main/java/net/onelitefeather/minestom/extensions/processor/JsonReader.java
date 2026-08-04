package net.onelitefeather.minestom.extensions.processor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, dependency-free JSON parser covering exactly what an {@code extension.json} can
 * contain: objects, arrays and strings.
 *
 * <p>The counterpart to {@link JsonWriter}. It exists so the build plugins can re-read a descriptor
 * without dragging a JSON library into their own dependencies, and so that reading and writing stay
 * in one place.
 *
 * <p>Numbers, booleans and {@code null} are rejected rather than silently coerced - the descriptor
 * format has no place for them, and quietly accepting them would let a typo through to the server.
 */
final class JsonReader {

    private final String input;
    private int pos;

    private JsonReader(String input) {
        this.input = input;
    }

    /**
     * Parses a JSON object.
     *
     * @param json the document
     * @return the parsed object; values are {@code String}, {@code List<Object>} or {@code Map<String, Object>}
     * @throws IllegalArgumentException if the document is not a well-formed JSON object of the
     *                                  supported subset
     */
    static Map<String, Object> parseObject(String json) {
        final JsonReader reader = new JsonReader(json);
        reader.skipWhitespace();
        final Map<String, Object> result = reader.readObject();
        reader.skipWhitespace();
        if (reader.pos != reader.input.length()) {
            throw reader.error("trailing content after the top-level object");
        }
        return result;
    }

    private Map<String, Object> readObject() {
        expect('{');
        final Map<String, Object> object = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return object;
        }
        while (true) {
            skipWhitespace();
            final String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            object.put(key, readValue());
            skipWhitespace();
            final char c = next();
            if (c == '}') {
                return object;
            }
            if (c != ',') {
                throw error("expected ',' or '}' but found '" + c + "'");
            }
        }
    }

    private List<Object> readArray() {
        expect('[');
        final List<Object> array = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return array;
        }
        while (true) {
            skipWhitespace();
            array.add(readValue());
            skipWhitespace();
            final char c = next();
            if (c == ']') {
                return array;
            }
            if (c != ',') {
                throw error("expected ',' or ']' but found '" + c + "'");
            }
        }
    }

    private Object readValue() {
        return switch (peek()) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            default -> throw error("only objects, arrays and strings are supported in extension.json");
        };
    }

    private String readString() {
        expect('"');
        final StringBuilder value = new StringBuilder();
        while (true) {
            final char c = next();
            if (c == '"') {
                return value.toString();
            }
            if (c != '\\') {
                value.append(c);
                continue;
            }
            final char escape = next();
            switch (escape) {
                case '"' -> value.append('"');
                case '\\' -> value.append('\\');
                case '/' -> value.append('/');
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> {
                    if (pos + 4 > input.length()) {
                        throw error("truncated unicode escape");
                    }
                    value.append((char) Integer.parseInt(input.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> throw error("unsupported escape '\\" + escape + "'");
            }
        }
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= input.length()) {
            throw error("unexpected end of document");
        }
        return input.charAt(pos);
    }

    private char next() {
        final char c = peek();
        pos++;
        return c;
    }

    private void expect(char expected) {
        final char c = next();
        if (c != expected) {
            throw error("expected '" + expected + "' but found '" + c + "'");
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("Invalid extension descriptor at offset " + pos + ": " + message);
    }
}
