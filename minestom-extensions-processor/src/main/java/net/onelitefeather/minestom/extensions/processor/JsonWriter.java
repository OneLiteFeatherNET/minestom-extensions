package net.onelitefeather.minestom.extensions.processor;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A minimal, dependency-free JSON writer that produces pretty printed output indented with two
 * spaces.
 *
 * <p>The processor module is deliberately free of runtime dependencies (see the module build file),
 * so it cannot use Gson - which is what reads the generated file again on the server side. This
 * class covers exactly the subset of JSON that {@code extension.json} needs: objects, arrays and
 * string values. There is no support for numbers, booleans or {@code null} because the target
 * format has no place for them.
 *
 * <p>Instances are not thread-safe and are meant to be used once and thrown away.
 */
final class JsonWriter {

    /** One level of indentation. */
    private static final String INDENT_UNIT = "  ";

    private final StringBuilder out = new StringBuilder();

    /**
     * One entry per open object/array. {@code Boolean.TRUE} means the scope is still empty, so the
     * next member must not be preceded by a comma. The size of the stack doubles as the current
     * indentation depth.
     */
    private final Deque<Boolean> emptyScopes = new ArrayDeque<>();

    /** {@code true} directly after {@link #name(String)}, where the value follows on the same line. */
    private boolean afterName;

    /**
     * Opens a JSON object.
     *
     * @return this writer
     */
    JsonWriter beginObject() {
        open('{');
        return this;
    }

    /**
     * Closes the most recently opened JSON object.
     *
     * @return this writer
     */
    JsonWriter endObject() {
        close('}');
        return this;
    }

    /**
     * Opens a JSON array.
     *
     * @return this writer
     */
    JsonWriter beginArray() {
        open('[');
        return this;
    }

    /**
     * Closes the most recently opened JSON array.
     *
     * @return this writer
     */
    JsonWriter endArray() {
        close(']');
        return this;
    }

    /**
     * Writes a member name inside the current object. The next written value belongs to it.
     *
     * @param name the member name
     * @return this writer
     */
    JsonWriter name(String name) {
        prepareMember();
        writeString(name);
        out.append(": ");
        afterName = true;
        return this;
    }

    /**
     * Writes a string value, escaped according to the JSON specification.
     *
     * @param value the value
     * @return this writer
     */
    JsonWriter value(String value) {
        prepareMember();
        writeString(value);
        return this;
    }

    /**
     * Writes a member consisting of a name and an array of string values.
     *
     * @param name   the member name
     * @param values the array elements
     * @return this writer
     */
    JsonWriter arrayMember(String name, Iterable<String> values) {
        name(name);
        beginArray();
        for (String value : values) {
            value(value);
        }
        return endArray();
    }

    /**
     * Writes a member consisting of a name and a string value.
     *
     * @param name  the member name
     * @param value the value
     * @return this writer
     */
    JsonWriter stringMember(String name, String value) {
        return name(name).value(value);
    }

    /**
     * Renders everything written so far, terminated by a single trailing newline so the file is well
     * behaved in a text editor.
     *
     * @return the JSON document
     */
    @Override
    public String toString() {
        return out + "\n";
    }

    private void open(char brace) {
        prepareMember();
        out.append(brace);
        emptyScopes.push(Boolean.TRUE);
    }

    private void close(char brace) {
        final boolean empty = emptyScopes.pop();
        if (!empty) {
            // A non-empty scope always ends on its own line, indented like its opening member.
            out.append('\n').append(INDENT_UNIT.repeat(emptyScopes.size()));
        }
        out.append(brace);
    }

    /**
     * Emits whatever has to precede the next member or array element: a comma if the enclosing scope
     * already has content, then a line break and the indentation for the current depth.
     */
    private void prepareMember() {
        if (afterName) {
            // The value of a member stays on the same line as its name.
            afterName = false;
            return;
        }
        if (emptyScopes.isEmpty()) {
            // Root value, nothing precedes it.
            return;
        }
        if (!emptyScopes.pop()) {
            out.append(',');
        }
        emptyScopes.push(Boolean.FALSE);
        out.append('\n').append(INDENT_UNIT.repeat(emptyScopes.size()));
    }

    /**
     * Appends a JSON string literal, escaping backslash, double quote, the short escapes for
     * {@code \b \f \n \r \t} and every character outside printable ASCII as {@code \ uXXXX}.
     *
     * <p>Escaping non-ASCII is deliberate rather than cosmetic. The descriptor is written through
     * the {@link javax.annotation.processing.Filer}, which encodes with javac's {@code -encoding}
     * setting, while {@code ExtensionManager} reads it back with the JVM default charset. A
     * consumer building with a legacy encoding would otherwise ship an author or dependency name
     * that is mojibake - or, for characters the build encoding cannot represent at all, silently
     * replaced by {@code ?} before the runtime ever sees it. Pure ASCII output is immune to both
     * ends of that mismatch. Characters outside the BMP are escaped as their two surrogate code
     * units, which is exactly the pair a JSON parser recombines.
     */
    private void writeString(String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20 || c > 0x7e) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
