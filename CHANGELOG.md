# Changelog

All notable changes to `io.ipwhois:ipwhois-java` will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2026-05-10

### Added

- Initial public release.
- `IPWhois.lookup()` — single IP lookup (IPv4 / IPv6, or the caller's own
  current IP when called with no argument or `null`).
- `IPWhois.bulkLookup()` — up to 100 IPs in a single GET request (paid plan).
  Returns a `BulkResult` wrapper exposing the success, per-IP results, and
  whole-batch error cases through `isSuccess()`, `getResults()`, and
  `getError()`.
- Localisation (`lang`), field filtering (`fields`), threat detection
  (`security`), and rate info (`rate`) — passable per call or set once on
  the client.
- Free plan (no API key, `ipwho.is`) and paid plan (`ipwhois.pro`) supported
  through the same class. A `null`, empty, or whitespace-only API key is
  normalised to the free plan.
- HTTPS by default. Set `Map.of("ssl", false)` in the constructor's options
  map to fall back to HTTP.
- Fluent setters for client-wide defaults — `setLanguage()`, `setFields()`,
  `setSecurity()`, `setRate()`, `setTimeout()`, `setConnectTimeout()`,
  `setUserAgent()`.
- Never-throws contract on `lookup()` and `bulkLookup()`: every runtime
  failure (invalid IP, bad API key, rate limit, network outage, malformed
  JSON) is returned in the response with `success: false`,
  a human-readable `message`, and an `error_type` of `"api"`, `"network"`,
  `"environment"`, or `"invalid_argument"`. HTTP error responses are
  additionally enriched with `http_status`; HTTP 429 responses on the free
  plan with `retry_after`.
- Fail-fast validation on the setters and the constructor's options map:
  `IllegalArgumentException` is thrown for `null` where it isn't allowed
  (`setLanguage`, `setFields`, `setUserAgent`, the `user_agent` option) and
  for non-positive timeouts (`setTimeout`, `setConnectTimeout`, and the
  `timeout` / `connect_timeout` options). Surfaces programmer errors at the
  point they happen rather than on the next network call.
- Zero runtime dependencies — uses only `java.net.http.HttpClient` (JDK 11+)
  and an embedded minimal JSON parser in `io.ipwhois.internal.MiniJson`. No
  Jackson, no Gson, no JSON-P.
- JUnit 5 test suite covering URL construction, input validation, fluent
  setters, and the embedded JSON parser.
