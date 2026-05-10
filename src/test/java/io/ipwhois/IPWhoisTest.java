package io.ipwhois;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests covering URL construction and input validation.
 * <p>
 * No real HTTP request is sent — {@code buildUrl()} is package-private so the
 * suite can be run anywhere without an API key or network access.
 */
final class IPWhoisTest {

    private static String buildUrl(IPWhois ipwhois, String path) {
        return ipwhois.buildUrl(path, Collections.emptyMap());
    }

    private static String buildUrl(IPWhois ipwhois, String path, Map<String, Object> options) {
        return ipwhois.buildUrl(path, options);
    }

    /* -------------------- URL construction -------------------- */

    @Test
    void freeEndpointHasNoApiKey() {
        IPWhois ipwhois = new IPWhois();
        String url = buildUrl(ipwhois, "/8.8.8.8");

        assertEquals("https://ipwho.is/8.8.8.8", url);
    }

    @Test
    void paidEndpointAppendsApiKey() {
        IPWhois ipwhois = new IPWhois("TESTKEY");
        String url = buildUrl(ipwhois, "/8.8.8.8");

        assertTrue(url.startsWith("https://ipwhois.pro/8.8.8.8?"), url);
        assertTrue(url.contains("key=TESTKEY"), url);
    }

    @Test
    void httpsIsAlwaysUsedByDefault() {
        assertTrue(buildUrl(new IPWhois(), "/").startsWith("https://"));
        assertTrue(buildUrl(new IPWhois("K"), "/").startsWith("https://"));
    }

    @Test
    void sslCanBeDisabled() {
        IPWhois free = new IPWhois(null, Map.of("ssl", false));
        IPWhois paid = new IPWhois("K", Map.of("ssl", false));

        assertTrue(buildUrl(free, "/").startsWith("http://ipwho.is"));
        assertTrue(buildUrl(paid, "/").startsWith("http://ipwhois.pro"));
    }

    @Test
    void sslDefaultsToTrueWhenNotPassed() {
        // Sanity check: omitting the option keeps HTTPS on.
        assertTrue(buildUrl(new IPWhois("K", Collections.emptyMap()), "/").startsWith("https://"));
    }

    @Test
    void fieldsAreJoinedWithCommas() {
        IPWhois ipwhois = new IPWhois("K");
        String url = buildUrl(ipwhois, "/8.8.8.8", Map.of(
                "fields", List.of("country", "city", "flag.emoji")
        ));

        // URLEncoder encodes commas as %2C — both forms are valid HTTP.
        assertTrue(url.contains("fields=country%2Ccity%2Cflag.emoji"), url);
    }

    @Test
    void fieldsCanBePassedAsStringArray() {
        IPWhois ipwhois = new IPWhois("K");
        String url = buildUrl(ipwhois, "/", Map.of(
                "fields", new String[]{"country", "city"}
        ));
        assertTrue(url.contains("fields=country%2Ccity"), url);
    }

    @Test
    void securityAndRateAreFlagsNotValues() {
        IPWhois ipwhois = new IPWhois("K");
        String url = buildUrl(ipwhois, "/", Map.of(
                "security", true,
                "rate", true
        ));

        assertTrue(url.contains("security=1"), url);
        assertTrue(url.contains("rate=1"), url);
    }

    @Test
    void securityFalseIsOmitted() {
        IPWhois ipwhois = new IPWhois("K");
        String url = buildUrl(ipwhois, "/", Map.of("security", false));

        assertFalse(url.contains("security="), url);
    }

    @Test
    void perCallOptionsOverrideDefaults() {
        IPWhois ipwhois = new IPWhois("K", Map.of("lang", "ru"));
        String url = buildUrl(ipwhois, "/", Map.of("lang", "en"));

        assertTrue(url.contains("lang=en"), url);
        assertFalse(url.contains("lang=ru"), url);
    }

    @Test
    void bulkUrlIsCommaSeparated() {
        IPWhois ipwhois = new IPWhois("K");
        String url = buildUrl(ipwhois, "/bulk/8.8.8.8,1.1.1.1");

        assertTrue(url.contains("/bulk/8.8.8.8,1.1.1.1"), url);
    }

    /* -------------------- API key normalisation -------------------- */

    @Test
    void emptyApiKeyIsTreatedAsFreePlan() {
        IPWhois ipwhois = new IPWhois("");
        String url = buildUrl(ipwhois, "/8.8.8.8");

        assertEquals("https://ipwho.is/8.8.8.8", url);
        assertFalse(url.contains("key="), url);
    }

    @Test
    void blankApiKeyIsTreatedAsFreePlan() {
        IPWhois ipwhois = new IPWhois("   ");
        String url = buildUrl(ipwhois, "/8.8.8.8");

        assertEquals("https://ipwho.is/8.8.8.8", url);
    }

    @Test
    void apiKeyIsTrimmedBeforeUse() {
        IPWhois ipwhois = new IPWhois("  TESTKEY  ");
        String url = buildUrl(ipwhois, "/8.8.8.8");

        assertTrue(url.startsWith("https://ipwhois.pro/8.8.8.8?"), url);
        assertTrue(url.contains("key=TESTKEY"), url);
        assertFalse(url.contains("key=%20"), url);
    }

    /* -------------------- Option validation -------------------- */

    @Test
    void invalidLanguageReturnsErrorMap() {
        Map<String, Object> result = new IPWhois().lookup("8.8.8.8", Map.of("lang", "klingon"));

        assertEquals(Boolean.FALSE, result.get("success"));
        assertEquals("invalid_argument", result.get("error_type"));
        assertTrue(result.get("message").toString().contains("klingon"),
                "Expected error message to mention 'klingon', got: " + result.get("message"));
    }

    @Test
    void bulkLookupRefusesEmptyList() {
        BulkResult result = new IPWhois("K").bulkLookup(Collections.emptyList());

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
        assertEquals("invalid_argument", result.getError().get("error_type"));
    }

    @Test
    void bulkLookupRefusesNullList() {
        BulkResult result = new IPWhois("K").bulkLookup(null);

        assertFalse(result.isSuccess());
        assertEquals("invalid_argument", result.getError().get("error_type"));
    }

    @Test
    void bulkLookupRefusesMoreThanLimit() {
        String[] tooMany = new String[IPWhois.BULK_LIMIT + 1];
        Arrays.fill(tooMany, "8.8.8.8");
        BulkResult result = new IPWhois("K").bulkLookup(Arrays.asList(tooMany));

        assertFalse(result.isSuccess());
        assertEquals("invalid_argument", result.getError().get("error_type"));
    }

    @Test
    void bulkLookupRefusesNullEntries() {
        BulkResult result = new IPWhois("K").bulkLookup(Arrays.asList("8.8.8.8", null, "1.1.1.1"));

        assertFalse(result.isSuccess());
        assertEquals("invalid_argument", result.getError().get("error_type"));
        assertTrue(result.getError().get("message").toString().contains("index 1"),
                result.getError().get("message").toString());
    }

    /* -------------------- Fluent setters -------------------- */

    @Test
    void fluentSettersReturnSelf() {
        IPWhois ipwhois = new IPWhois();

        assertSame(ipwhois, ipwhois.setLanguage("en"));
        assertSame(ipwhois, ipwhois.setFields(List.of("country")));
        assertSame(ipwhois, ipwhois.setSecurity(true));
        assertSame(ipwhois, ipwhois.setRate(false));
        assertSame(ipwhois, ipwhois.setTimeout(5));
        assertSame(ipwhois, ipwhois.setConnectTimeout(2));
        assertSame(ipwhois, ipwhois.setUserAgent("test/1.0"));
    }

    @Test
    void setLanguageAffectsSubsequentRequests() {
        IPWhois ipwhois = new IPWhois("K").setLanguage("de");
        String url = buildUrl(ipwhois, "/");

        assertTrue(url.contains("lang=de"), url);
    }

    @Test
    void setFieldsAffectsSubsequentRequests() {
        IPWhois ipwhois = new IPWhois("K").setFields(List.of("success", "country"));
        String url = buildUrl(ipwhois, "/8.8.8.8");

        assertTrue(url.contains("fields=success%2Ccountry"), url);
    }

    @Test
    void constructorOptionsApplyToDefaults() {
        Map<String, Object> opts = new HashMap<>();
        opts.put("lang", "ja");
        opts.put("security", true);
        opts.put("rate", true);

        IPWhois ipwhois = new IPWhois("K", opts);
        String url = buildUrl(ipwhois, "/");

        assertTrue(url.contains("lang=ja"), url);
        assertTrue(url.contains("security=1"), url);
        assertTrue(url.contains("rate=1"), url);
    }

    /* -------------------- Setter validation (IAE) -------------------- */

    @Test
    void setLanguageRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new IPWhois().setLanguage(null));
    }

    @Test
    void setFieldsRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new IPWhois().setFields(null));
    }

    @Test
    void setUserAgentRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new IPWhois().setUserAgent(null));
    }

    @Test
    void setTimeoutRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> new IPWhois().setTimeout(0));
    }

    @Test
    void setTimeoutRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new IPWhois().setTimeout(-1));
    }

    @Test
    void setConnectTimeoutRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> new IPWhois().setConnectTimeout(0));
    }

    @Test
    void setConnectTimeoutRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new IPWhois().setConnectTimeout(-1));
    }

    @Test
    void constructorOptionRejectsZeroTimeout() {
        assertThrows(IllegalArgumentException.class,
                () -> new IPWhois(null, Map.of("timeout", 0)));
    }

    @Test
    void constructorOptionRejectsZeroConnectTimeout() {
        assertThrows(IllegalArgumentException.class,
                () -> new IPWhois(null, Map.of("connect_timeout", 0)));
    }

    @Test
    void constructorOptionRejectsNullUserAgent() {
        Map<String, Object> opts = new HashMap<>();
        opts.put("user_agent", null);
        assertThrows(IllegalArgumentException.class, () -> new IPWhois(null, opts));
    }

    /* -------------------- Misc -------------------- */

    @Test
    void supportedLanguagesAreImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> IPWhois.SUPPORTED_LANGUAGES.add("klingon"));
    }

    @Test
    void versionIsSet() {
        assertNotNull(IPWhois.VERSION);
        assertFalse(IPWhois.VERSION.isEmpty());
    }
}
