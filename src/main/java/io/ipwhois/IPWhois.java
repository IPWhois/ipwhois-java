package io.ipwhois;

import io.ipwhois.internal.MiniJson;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Java client for the ipwhois.io IP Geolocation API.
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 *   // Free plan (no API key, ~1 request/second per client IP)
 *   IPWhois ipwhois = new IPWhois();
 *   Map<String, Object> info = ipwhois.lookup("8.8.8.8");
 *
 *   // Paid plan (with API key, higher limits, bulk, security data, …)
 *   IPWhois ipwhois = new IPWhois("YOUR_API_KEY");
 *   Map<String, Object> info = ipwhois.lookup("8.8.8.8", Map.of(
 *       "lang", "en",
 *       "security", true
 *   ));
 *
 *   // Bulk lookup — up to 100 IPs in one call (paid only)
 *   BulkResult bulk = ipwhois.bulkLookup(List.of("8.8.8.8", "1.1.1.1", "208.67.222.222"));
 *
 *   // HTTPS is enabled by default. Pass Map.of("ssl", false) to fall back to HTTP.
 * }</pre>
 *
 * <h2>Error handling</h2>
 * <p>{@link #lookup} and {@link #bulkLookup} never throw on any runtime
 * failure — invalid IP, network outage, rate limit, bad API key, malformed
 * response, missing JDK feature, anything. Every failure comes back in the
 * response with {@code success = false}, a human-readable {@code message},
 * and an {@code error_type} you can branch on. Just check
 * {@code info.get("success")} after every call.
 *
 * <p>Setter methods and the constructor <em>do</em> validate their arguments
 * and throw {@link IllegalArgumentException} on clearly invalid input
 * ({@code null} where forbidden, non-positive timeouts). This is fail-fast:
 * programmer errors surface at the point they happen, not on the next
 * network call.
 */
public final class IPWhois {

    /** Library version, used in the default User-Agent header. */
    public static final String VERSION = "1.2.0";

    /** Free-plan endpoint host (used when no API key is provided). */
    public static final String HOST_FREE = "ipwho.is";

    /** Paid-plan endpoint host (used when an API key is provided). */
    public static final String HOST_PAID = "ipwhois.pro";

    /** Maximum number of IP addresses allowed in a single bulk request. */
    public static final int BULK_LIMIT = 100;

    /** Languages supported by the {@code lang} parameter. */
    public static final List<String> SUPPORTED_LANGUAGES =
            Collections.unmodifiableList(Arrays.asList(
                    "en", "ru", "de", "es", "pt-BR", "fr", "zh-CN", "ja"
            ));

    private String userAgent = "ipwhois-java/" + VERSION;
    private int timeoutSeconds = 10;
    private int connectTimeoutSeconds = 5;
    private boolean ssl = true;
    private final String apiKey;

    /** Default options applied to every request unless overridden. */
    private final Map<String, Object> defaults = new HashMap<>();

    /** Free-plan client with no defaults. Equivalent to {@code new IPWhois(null)}. */
    public IPWhois() {
        this(null, Collections.emptyMap());
    }

    /**
     * Client using {@code apiKey} (or free plan if {@code null}/blank) with no defaults.
     *
     * @param apiKey your ipwhois.io API key; {@code null}, empty, or whitespace-only
     *               selects the free plan
     */
    public IPWhois(String apiKey) {
        this(apiKey, Collections.emptyMap());
    }

    /**
     * Build a client with an API key and a map of default options.
     *
     * @param apiKey  Your ipwhois.io API key. Pass {@code null} (or a blank
     *                string) for the free plan.
     * @param options Optional defaults applied to every request.
     *                Recognised keys: {@code lang}, {@code fields}, {@code security},
     *                {@code rate}, {@code ssl}, {@code timeout}, {@code connect_timeout},
     *                {@code user_agent}.
     * @throws IllegalArgumentException if any of {@code timeout},
     *         {@code connect_timeout} is non-positive, or {@code user_agent}
     *         is {@code null}.
     */
    public IPWhois(String apiKey, Map<String, Object> options) {
        // Normalise the API key: null, empty, or whitespace-only → free plan.
        // Avoids surprises like new IPWhois("") routing to the paid host with
        // an empty key= query parameter.
        this.apiKey = (apiKey == null || apiKey.trim().isEmpty()) ? null : apiKey.trim();

        Map<String, Object> opts = new HashMap<>(options == null ? Collections.emptyMap() : options);

        if (opts.containsKey("timeout")) {
            int v = toInt(opts.remove("timeout"), this.timeoutSeconds);
            if (v <= 0) {
                throw new IllegalArgumentException(
                        "timeout option must be > 0 seconds, got " + v);
            }
            this.timeoutSeconds = v;
        }
        if (opts.containsKey("connect_timeout")) {
            int v = toInt(opts.remove("connect_timeout"), this.connectTimeoutSeconds);
            if (v <= 0) {
                throw new IllegalArgumentException(
                        "connect_timeout option must be > 0 seconds, got " + v);
            }
            this.connectTimeoutSeconds = v;
        }
        if (opts.containsKey("user_agent")) {
            Object ua = opts.remove("user_agent");
            if (ua == null) {
                throw new IllegalArgumentException("user_agent option must not be null");
            }
            this.userAgent = ua.toString();
        }
        if (opts.containsKey("ssl")) {
            this.ssl = toBool(opts.remove("ssl"), this.ssl);
        }

        this.defaults.putAll(opts);
    }

    /**
     * Look up the caller's own public IP address.
     * <p>
     * Never throws — check {@code result.get("success")} after every call.
     *
     * @return the decoded JSON response. On any error (API, network) the map
     *         contains {@code success = false} and {@code message}.
     */
    public Map<String, Object> lookup() {
        return lookup(null, Collections.emptyMap());
    }

    /**
     * Look up information for a single IP address with no per-call options.
     *
     * @param ip IPv4 or IPv6 address; {@code null} = the caller's own current IP
     * @return the decoded JSON response. On any error the map contains
     *         {@code success = false} and {@code message}.
     */
    public Map<String, Object> lookup(String ip) {
        return lookup(ip, Collections.emptyMap());
    }

    /**
     * Look up information for a single IP address.
     * <p>
     * Pass {@code null} (or call {@link #lookup()}) to look up the caller's own
     * public IP, as documented at https://ipwhois.io/documentation.
     * <p>
     * Never throws — check {@code result.get("success")} after every call.
     *
     * @param ip      IPv4 or IPv6 address. {@code null} = current IP.
     * @param options Per-call options: {@code lang}, {@code fields},
     *                {@code security} (bool), {@code rate} (bool).
     * @return Decoded JSON response. On any error (API, network, bad input)
     *         the map contains {@code success = false} and {@code message}.
     */
    public Map<String, Object> lookup(String ip, Map<String, Object> options) {
        try {
            if (options == null) options = Collections.emptyMap();

            Map<String, Object> err = validateOptions(options);
            if (err != null) return err;

            String path = ip != null ? "/" + encodePath(ip) : "/";
            String url = buildUrl(path, options);

            Object decoded = request(url);
            if (decoded instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) decoded;
                return m;
            }

            // Unexpected: the single-IP endpoint returned a non-object.
            return errorMap("Unexpected non-object response from ipwhois API", "api");
        } catch (RuntimeException e) {
            // Safety net — the public API never escapes a runtime exception,
            // even if a future change accidentally leaks one.
            return errorMap(
                    "Unexpected error: " + describeThrowable(e),
                    "environment");
        }
    }

    /**
     * Look up information for multiple IP addresses in a single request.
     * <p>
     * Uses the GET / comma-separated form documented at
     * https://ipwhois.io/documentation/bulk — up to 100 addresses per call.
     * Each address counts as one credit. Available on the Business and
     * Unlimited plans only.
     * <p>
     * Per-IP errors are returned inline on the result, with {@code success = false}
     * for the affected entry; the rest of the batch is still usable. If the whole
     * call fails, {@link BulkResult#isSuccess()} returns {@code false} and
     * {@link BulkResult#getError()} contains the error map.
     * <p>
     * Never throws.
     *
     * @param ips Up to 100 IPv4/IPv6 addresses (mixable, no {@code null} entries).
     * @return a {@link BulkResult} wrapping either the per-IP results
     *         ({@link BulkResult#getResults()}) or the whole-batch error map
     *         ({@link BulkResult#getError()}).
     */
    public BulkResult bulkLookup(List<String> ips) {
        return bulkLookup(ips, Collections.emptyMap());
    }

    /**
     * Look up information for multiple IP addresses with per-call options.
     *
     * @param ips     Up to 100 IPv4/IPv6 addresses (mixable, no {@code null} entries).
     * @param options Per-call options (same keys as {@link #lookup}).
     * @return a {@link BulkResult} wrapping either the per-IP results
     *         ({@link BulkResult#getResults()}) or the whole-batch error map
     *         ({@link BulkResult#getError()}).
     */
    public BulkResult bulkLookup(List<String> ips, Map<String, Object> options) {
        try {
            if (options == null) options = Collections.emptyMap();

            if (ips == null || ips.isEmpty()) {
                return BulkResult.error(errorMap(
                        "Bulk lookup requires at least one IP address.",
                        "invalid_argument"
                ));
            }
            if (ips.size() > BULK_LIMIT) {
                return BulkResult.error(errorMap(
                        String.format("Bulk lookup accepts at most %d IP addresses per call, got %d.",
                                BULK_LIMIT, ips.size()),
                        "invalid_argument"
                ));
            }
            for (int i = 0; i < ips.size(); i++) {
                if (ips.get(i) == null) {
                    return BulkResult.error(errorMap(
                            "Bulk lookup IP at index " + i + " is null.",
                            "invalid_argument"
                    ));
                }
            }

            Map<String, Object> err = validateOptions(options);
            if (err != null) return BulkResult.error(err);

            // The API accepts addresses joined by commas — no URL-encoding of the
            // commas themselves, otherwise the path is misinterpreted.
            StringBuilder joined = new StringBuilder();
            for (int i = 0; i < ips.size(); i++) {
                if (i > 0) joined.append(',');
                joined.append(encodePath(ips.get(i)));
            }
            String url = buildUrl("/bulk/" + joined, options);

            Object decoded = request(url);

            if (decoded instanceof List) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Object item : (List<?>) decoded) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) item;
                        rows.add(m);
                    } else {
                        // Defensive: per-IP entry came back as something other than an object.
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("success", false);
                        row.put("message", "Unexpected non-object entry in bulk response");
                        row.put("error_type", "api");
                        rows.add(row);
                    }
                }
                return BulkResult.success(rows);
            }

            // Whole-batch failure — the response is a single error map.
            if (decoded instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) decoded;
                return BulkResult.error(m);
            }

            return BulkResult.error(errorMap(
                    "Unexpected response shape from ipwhois bulk API", "api"));
        } catch (RuntimeException e) {
            return BulkResult.error(errorMap(
                    "Unexpected error: " + describeThrowable(e),
                    "environment"));
        }
    }

    /**
     * Set the default language used when none is supplied per call.
     *
     * @param lang one of {@link #SUPPORTED_LANGUAGES}; must not be {@code null}
     * @return this client, for chaining
     * @throws IllegalArgumentException if {@code lang} is {@code null}.
     */
    public IPWhois setLanguage(String lang) {
        if (lang == null) {
            throw new IllegalArgumentException("lang must not be null");
        }
        this.defaults.put("lang", lang);
        return this;
    }

    /**
     * Restrict every response to a fixed set of fields by default.
     * <p>
     * Include {@code "success"} in the list if you rely on {@code info.get("success")}
     * for error checking — when {@code fields} is set, the API only returns the
     * fields you ask for.
     *
     * @param fields For example: {@code List.of("success", "country", "city", "flag.emoji")}.
     *               Must not be {@code null}.
     * @return this client, for chaining
     * @throws IllegalArgumentException if {@code fields} is {@code null}.
     */
    public IPWhois setFields(List<String> fields) {
        if (fields == null) {
            throw new IllegalArgumentException("fields must not be null");
        }
        this.defaults.put("fields", new ArrayList<>(fields));
        return this;
    }

    /**
     * Enable or disable threat-detection data on every call by default.
     *
     * @param enabled whether to include the {@code security} block in responses
     * @return this client, for chaining
     */
    public IPWhois setSecurity(boolean enabled) {
        this.defaults.put("security", enabled);
        return this;
    }

    /**
     * Enable or disable the {@code rate} block in responses by default.
     *
     * @param enabled whether to include the {@code rate} block in responses
     * @return this client, for chaining
     */
    public IPWhois setRate(boolean enabled) {
        this.defaults.put("rate", enabled);
        return this;
    }

    /**
     * Set the per-request total timeout in seconds (default: 10).
     *
     * @param seconds total request timeout in seconds; must be positive
     * @return this client, for chaining
     * @throws IllegalArgumentException if {@code seconds} is not positive.
     */
    public IPWhois setTimeout(int seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("timeout must be > 0 seconds, got " + seconds);
        }
        this.timeoutSeconds = seconds;
        return this;
    }

    /**
     * Set the connection timeout in seconds (default: 5).
     *
     * @param seconds TCP connect timeout in seconds; must be positive
     * @return this client, for chaining
     * @throws IllegalArgumentException if {@code seconds} is not positive.
     */
    public IPWhois setConnectTimeout(int seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("connect_timeout must be > 0 seconds, got " + seconds);
        }
        this.connectTimeoutSeconds = seconds;
        return this;
    }

    /**
     * Override the User-Agent header sent with every request.
     *
     * @param userAgent the User-Agent header value; must not be {@code null}
     * @return this client, for chaining
     * @throws IllegalArgumentException if {@code userAgent} is {@code null}.
     */
    public IPWhois setUserAgent(String userAgent) {
        if (userAgent == null) {
            throw new IllegalArgumentException("user_agent must not be null");
        }
        this.userAgent = userAgent;
        return this;
    }

    /* ------------------------------------------------------------------ */
    /* Internals                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Validate per-call options. Returns an error map on the first invalid
     * option, or {@code null} if everything looks OK.
     */
    private Map<String, Object> validateOptions(Map<String, Object> options) {
        Map<String, Object> merged = new HashMap<>(defaults);
        merged.putAll(options);

        Object langObj = merged.get("lang");
        if (langObj != null) {
            String lang = langObj.toString();
            if (!SUPPORTED_LANGUAGES.contains(lang)) {
                return errorMap(
                        String.format("Unsupported language \"%s\". Supported: %s.",
                                lang, String.join(", ", SUPPORTED_LANGUAGES)),
                        "invalid_argument"
                );
            }
        }
        return null;
    }

    /** Build the full URL for a given path + options. Package-private for tests. */
    String buildUrl(String path, Map<String, Object> options) {
        String host = apiKey != null ? HOST_PAID : HOST_FREE;

        // Per-call options win over defaults.
        Map<String, Object> merged = new LinkedHashMap<>(defaults);
        merged.putAll(options);

        // Build the query in a stable order: key, lang, fields, security, rate.
        LinkedHashMap<String, String> query = new LinkedHashMap<>();

        if (apiKey != null) {
            query.put("key", apiKey);
        }
        if (merged.get("lang") != null) {
            query.put("lang", merged.get("lang").toString());
        }
        if (merged.get("fields") != null) {
            query.put("fields", joinFields(merged.get("fields")));
        }
        if (toBool(merged.get("security"), false)) {
            query.put("security", "1");
        }
        if (toBool(merged.get("rate"), false)) {
            query.put("rate", "1");
        }

        StringBuilder url = new StringBuilder();
        url.append(ssl ? "https" : "http").append("://").append(host).append(path);
        if (!query.isEmpty()) {
            url.append('?');
            boolean first = true;
            for (Map.Entry<String, String> e : query.entrySet()) {
                if (!first) url.append('&');
                url.append(encodeQuery(e.getKey()))
                        .append('=')
                        .append(encodeQuery(e.getValue()));
                first = false;
            }
        }
        return url.toString();
    }

    /**
     * Perform a GET request and return the decoded JSON body.
     * The result is one of: {@code Map<String, Object>} (object responses and
     * synthesised errors), or {@code List<Object>} (the bulk endpoint).
     * <p>
     * Never throws — any unexpected exception is converted into an error map.
     */
    private Object request(String url) {
        HttpClient client;
        HttpRequest req;
        try {
            client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            return errorMap("Invalid request configuration: " + describeThrowable(e),
                    "invalid_argument");
        } catch (RuntimeException e) {
            return errorMap("Failed to build HTTP request: " + describeThrowable(e),
                    "environment");
        }

        HttpResponse<String> response;
        try {
            response = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return errorMap("Network error: " + describeThrowable(e), "network");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return errorMap("Request interrupted: " + describeThrowable(e), "network");
        } catch (RuntimeException e) {
            return errorMap("Network error: " + describeThrowable(e), "network");
        }

        int statusCode = response.statusCode();
        String body = response.body();

        Object decoded;
        try {
            decoded = (body == null || body.isEmpty()) ? null : MiniJson.parse(body);
        } catch (MiniJson.JsonParseException e) {
            // The ipwhois API always returns JSON. A non-JSON body means
            // something went wrong upstream (gateway error page, captive
            // portal, hijacked response, …) — synthesise an error map so
            // the caller can handle it the same way as a normal API error.
            String snippet = body == null ? "" : body.replaceAll("\\s+", " ").trim();
            if (snippet.length() > 200) snippet = snippet.substring(0, 200) + "…";
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("message", String.format("Invalid JSON returned by ipwhois API (HTTP %d): %s",
                    statusCode, snippet));
            err.put("http_status", statusCode);
            err.put("error_type", "api");
            return err;
        }

        if (decoded == null) {
            decoded = new LinkedHashMap<String, Object>();
        } else if (!(decoded instanceof Map) && !(decoded instanceof List)) {
            // Wrap scalar in a value object so the rest of the pipeline can
            // assume a structured response.
            Map<String, Object> wrap = new LinkedHashMap<>();
            wrap.put("value", decoded);
            decoded = wrap;
        }

        if (decoded instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) decoded;

            // For HTTP errors, normalise into a `success: false` map so the
            // caller doesn't have to inspect HTTP status separately.
            if (statusCode >= 400) {
                if (Boolean.FALSE.equals(m.get("success"))) {
                    // The API already shaped the error correctly — just enrich it.
                    m.put("http_status", statusCode);
                } else {
                    String message = m.get("message") != null
                            ? m.get("message").toString()
                            : String.format("HTTP %d returned by ipwhois API", statusCode);
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("success", false);
                    err.put("message", message);
                    err.put("http_status", statusCode);
                    m = err;
                    decoded = m;
                }

                // `Retry-After` is only emitted by the free-plan endpoint
                // (ipwho.is); the paid endpoint (ipwhois.pro) does not send
                // the header, so don't try to read it there.
                if (statusCode == 429 && apiKey == null) {
                    Optional<String> retryAfter = response.headers().firstValue("retry-after");
                    if (retryAfter.isPresent()) {
                        try {
                            m.put("retry_after", Integer.parseInt(retryAfter.get().trim()));
                        } catch (NumberFormatException ignored) {
                            // Non-numeric Retry-After (HTTP-date form) — skip.
                        }
                    }
                }
            }

            // Tag every API-shaped error (`success: false` returned by the API,
            // on any HTTP status) with `error_type = "api"` so callers can branch
            // on the category alongside the non-API codes ("network",
            // "environment", "invalid_argument"). HTTP 2xx + success=false bodies
            // (e.g. "Invalid IP address", "Reserved range") are otherwise passed
            // through untouched.
            if (Boolean.FALSE.equals(m.get("success")) && !m.containsKey("error_type")) {
                m.put("error_type", "api");
            }
        }

        return decoded;
    }

    /* ------------------------------------------------------------------ */
    /* Small helpers                                                      */
    /* ------------------------------------------------------------------ */

    private static Map<String, Object> errorMap(String message, String errorType) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message);
        m.put("error_type", errorType);
        return m;
    }

    /** Describe a throwable for inclusion in an error message; never returns null. */
    private static String describeThrowable(Throwable t) {
        String msg = t.getMessage();
        return msg != null ? msg : t.getClass().getSimpleName();
    }

    private static String joinFields(Object fields) {
        if (fields instanceof Collection) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Object f : (Collection<?>) fields) {
                if (!first) sb.append(',');
                sb.append(f == null ? "" : f.toString());
                first = false;
            }
            return sb.toString();
        }
        if (fields instanceof String[]) {
            return String.join(",", (String[]) fields);
        }
        return fields.toString();
    }

    private static int toInt(Object v, int fallback) {
        if (v == null) return fallback;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean toBool(Object v, boolean fallback) {
        if (v == null) return fallback;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        String s = v.toString();
        return "1".equals(s) || "true".equalsIgnoreCase(s);
    }

    /**
     * Percent-encode a path segment. Java's URLEncoder uses
     * {@code application/x-www-form-urlencoded} encoding (spaces → '+'),
     * which is correct for query values but wrong for path segments — we
     * convert {@code '+'} back to {@code "%20"} so spaces are encoded
     * the way RFC 3986 expects in paths.
     */
    private static String encodePath(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Encode a query string component. */
    private static String encodeQuery(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
