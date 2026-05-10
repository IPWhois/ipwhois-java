package io.ipwhois.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spot-checks for the embedded JSON parser. The ipwhois.io response shape is
 * small and well-defined, but we still exercise the basics here.
 */
final class MiniJsonTest {

    @Test
    void parsesBasicScalars() {
        assertNull(MiniJson.parse("null"));
        assertEquals(Boolean.TRUE, MiniJson.parse("true"));
        assertEquals(Boolean.FALSE, MiniJson.parse("false"));
        assertEquals("hello", MiniJson.parse("\"hello\""));
        assertEquals(42L, MiniJson.parse("42"));
        assertEquals(-7L, MiniJson.parse("-7"));
        assertEquals(3.14, (Double) MiniJson.parse("3.14"), 0.0001);
        assertEquals(1.5e10, (Double) MiniJson.parse("1.5e10"), 0.0001);
    }

    @Test
    void parsesStringEscapes() {
        assertEquals("a\nb",   MiniJson.parse("\"a\\nb\""));
        assertEquals("\"q\"",  MiniJson.parse("\"\\\"q\\\"\""));
        assertEquals("/",      MiniJson.parse("\"\\/\""));
        assertEquals("A",      MiniJson.parse("\"\\u0041\""));
    }

    @Test
    void parsesObjectsPreservingInsertionOrder() {
        Object parsed = MiniJson.parse("{\"a\":1,\"b\":2,\"c\":3}");
        assertTrue(parsed instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) parsed;
        assertEquals(List.of("a", "b", "c"), List.copyOf(m.keySet()));
        assertEquals(1L, m.get("a"));
    }

    @Test
    void parsesArrays() {
        Object parsed = MiniJson.parse("[1, 2, 3]");
        assertTrue(parsed instanceof List);
        assertEquals(List.of(1L, 2L, 3L), parsed);
    }

    @Test
    void parsesNestedShapes() {
        String json = "{"
                + "\"ip\":\"8.8.8.8\","
                + "\"success\":true,"
                + "\"flag\":{\"emoji\":\"🇺🇸\",\"img\":\"https://cdn.example/us.svg\"},"
                + "\"borders\":[\"CA\",\"MX\"]"
                + "}";
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) MiniJson.parse(json);

        assertEquals("8.8.8.8", m.get("ip"));
        assertEquals(Boolean.TRUE, m.get("success"));

        @SuppressWarnings("unchecked")
        Map<String, Object> flag = (Map<String, Object>) m.get("flag");
        assertEquals("🇺🇸", flag.get("emoji"));

        @SuppressWarnings("unchecked")
        List<Object> borders = (List<Object>) m.get("borders");
        assertEquals(List.of("CA", "MX"), borders);
    }

    @Test
    void rejectsTrailingGarbage() {
        assertThrows(MiniJson.JsonParseException.class, () -> MiniJson.parse("{}garbage"));
    }

    @Test
    void rejectsUnterminatedString() {
        assertThrows(MiniJson.JsonParseException.class, () -> MiniJson.parse("\"oops"));
    }

    @Test
    void rejectsNull() {
        assertThrows(MiniJson.JsonParseException.class, () -> MiniJson.parse(null));
    }

    /* -------------------- Hardened number parsing -------------------- */

    @Test
    void rejectsLoneMinus() {
        assertThrows(MiniJson.JsonParseException.class, () -> MiniJson.parse("-"));
    }

    @Test
    void rejectsTrailingDot() {
        assertThrows(MiniJson.JsonParseException.class, () -> MiniJson.parse("1."));
    }

    @Test
    void rejectsExponentWithoutDigits() {
        assertThrows(MiniJson.JsonParseException.class, () -> MiniJson.parse("1e"));
    }

    @Test
    void rejectsExponentSignWithoutDigits() {
        assertThrows(MiniJson.JsonParseException.class, () -> MiniJson.parse("1e+"));
        assertThrows(MiniJson.JsonParseException.class, () -> MiniJson.parse("1e-"));
    }

    @Test
    void rejectsMinusDot() {
        assertThrows(MiniJson.JsonParseException.class, () -> MiniJson.parse("-."));
    }

    @Test
    void acceptsValidEdgeCases() {
        assertEquals(0L,   MiniJson.parse("0"));
        assertEquals(-0.5, (Double) MiniJson.parse("-0.5"), 0.0001);
        assertEquals(1.0e-10, (Double) MiniJson.parse("1.0e-10"), 1e-20);
    }
}
