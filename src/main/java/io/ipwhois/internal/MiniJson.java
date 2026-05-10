package io.ipwhois.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny recursive-descent JSON parser used internally by {@code IPWhois}.
 * <p>
 * Keeps the library dependency-free: no Jackson, no Gson, no JSON-P. The
 * ipwhois.io API returns a small, well-defined JSON shape, so a full-featured
 * parser would be overkill.
 * <p>
 * The parser is permissive in input (accepts standard JSON whitespace,
 * unicode escapes, scientific notation) and produces the following Java types:
 * <ul>
 *   <li>{@code null} → {@code null}</li>
 *   <li>{@code true} / {@code false} → {@link Boolean}</li>
 *   <li>integers → {@link Long}, falls back to {@link Double} on overflow</li>
 *   <li>fractions / exponents → {@link Double}</li>
 *   <li>strings → {@link String}</li>
 *   <li>arrays → {@link List List&lt;Object&gt;}</li>
 *   <li>objects → {@link LinkedHashMap LinkedHashMap&lt;String, Object&gt;}
 *       (insertion order preserved)</li>
 * </ul>
 *
 * <p>Not intended for public use — kept in {@code io.ipwhois.internal} on
 * purpose. The shape of this class is not part of the SDK's stable API.
 */
public final class MiniJson {

    /** Unchecked exception thrown when the input is not valid JSON. */
    public static final class JsonParseException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        /**
         * Build a parse exception with a human-readable message.
         *
         * @param message human-readable description of the parse failure,
         *                typically including the position in the input
         */
        public JsonParseException(String message) {
            super(message);
        }
    }

    private final String src;
    private int pos;

    private MiniJson(String src) {
        this.src = src;
        this.pos = 0;
    }

    /**
     * Parse a JSON document into Java values.
     * <p>
     * Returned types are: {@code null}, {@link Boolean}, {@link Long} or
     * {@link Double} (numbers), {@link String}, {@link java.util.LinkedHashMap}
     * (objects, preserving key insertion order), and {@link java.util.ArrayList}
     * (arrays).
     *
     * @param json JSON document to parse; must not be {@code null}
     * @return the parsed value (which may itself be {@code null} for a literal
     *         {@code null} document)
     * @throws JsonParseException if the input is {@code null} or not a valid
     *         JSON document (including trailing garbage after the root value)
     */
    public static Object parse(String json) {
        if (json == null) {
            throw new JsonParseException("Input is null");
        }
        MiniJson m = new MiniJson(json);
        m.skipWhitespace();
        Object value = m.parseValue();
        m.skipWhitespace();
        if (m.pos != m.src.length()) {
            throw new JsonParseException("Unexpected trailing content at position " + m.pos);
        }
        return value;
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= src.length()) {
            throw new JsonParseException("Unexpected end of input");
        }
        char c = src.charAt(pos);
        switch (c) {
            case '{': return parseObject();
            case '[': return parseArray();
            case '"': return parseString();
            case 't': case 'f': return parseBoolean();
            case 'n': return parseNull();
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return parseNumber();
                }
                throw new JsonParseException(
                        "Unexpected character '" + c + "' at position " + pos);
        }
    }

    private Map<String, Object> parseObject() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw new JsonParseException("Expected string key at position " + pos);
            }
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            char next = nextChar();
            if (next == ',') continue;
            if (next == '}') return map;
            throw new JsonParseException(
                    "Expected ',' or '}' in object at position " + (pos - 1));
        }
    }

    private List<Object> parseArray() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            Object value = parseValue();
            list.add(value);
            skipWhitespace();
            char next = nextChar();
            if (next == ',') continue;
            if (next == ']') return list;
            throw new JsonParseException(
                    "Expected ',' or ']' in array at position " + (pos - 1));
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= src.length()) {
                    throw new JsonParseException("Bad escape at end of input");
                }
                char esc = src.charAt(pos++);
                switch (esc) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 > src.length()) {
                            throw new JsonParseException("Bad \\u escape at position " + pos);
                        }
                        String hex = src.substring(pos, pos + 4);
                        pos += 4;
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException nfe) {
                            throw new JsonParseException("Invalid \\u escape: \\u" + hex);
                        }
                        break;
                    default:
                        throw new JsonParseException("Bad escape \\" + esc);
                }
            } else {
                sb.append(c);
            }
        }
        throw new JsonParseException("Unterminated string starting before position " + pos);
    }

    private Object parseNumber() {
        int start = pos;
        if (peek() == '-') pos++;

        // Integer part: at least one digit required after the optional minus.
        if (pos >= src.length() || !isDigit(src.charAt(pos))) {
            throw new JsonParseException("Expected digit at position " + pos);
        }
        while (pos < src.length() && isDigit(src.charAt(pos))) pos++;

        boolean isFloat = false;
        if (pos < src.length() && src.charAt(pos) == '.') {
            isFloat = true;
            pos++;
            // Fraction part: at least one digit required after the dot.
            if (pos >= src.length() || !isDigit(src.charAt(pos))) {
                throw new JsonParseException("Expected digit after '.' at position " + pos);
            }
            while (pos < src.length() && isDigit(src.charAt(pos))) pos++;
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            isFloat = true;
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
            // Exponent: at least one digit required after e/E and the optional sign.
            if (pos >= src.length() || !isDigit(src.charAt(pos))) {
                throw new JsonParseException("Expected digit in exponent at position " + pos);
            }
            while (pos < src.length() && isDigit(src.charAt(pos))) pos++;
        }

        String numStr = src.substring(start, pos);
        try {
            if (isFloat) {
                return Double.parseDouble(numStr);
            }
            return Long.parseLong(numStr);
        } catch (NumberFormatException e) {
            // Integer overflow → fall back to Double (still finite for typical inputs).
            if (!isFloat) {
                try {
                    return Double.parseDouble(numStr);
                } catch (NumberFormatException ignored) {
                    // fall through to the JsonParseException below
                }
            }
            throw new JsonParseException("Invalid number '" + numStr + "' at position " + start);
        }
    }

    private Boolean parseBoolean() {
        if (src.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (src.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw new JsonParseException("Expected boolean literal at position " + pos);
    }

    private Object parseNull() {
        if (src.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new JsonParseException("Expected null at position " + pos);
    }

    private void expect(char c) {
        if (pos >= src.length() || src.charAt(pos) != c) {
            throw new JsonParseException("Expected '" + c + "' at position " + pos);
        }
        pos++;
    }

    private char nextChar() {
        if (pos >= src.length()) {
            throw new JsonParseException("Unexpected end of input");
        }
        return src.charAt(pos++);
    }

    private char peek() {
        if (pos >= src.length()) {
            throw new JsonParseException("Unexpected end of input");
        }
        return src.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
