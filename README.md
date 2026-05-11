# ipwhois-java

[![Maven Central](https://img.shields.io/maven-central/v/io.ipwhois/ipwhois-java.svg)](https://central.sonatype.com/artifact/io.ipwhois/ipwhois-java)
[![Java Version](https://img.shields.io/badge/java-11%2B-blue.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Official, dependency-free Java client for the [ipwhois.io](https://ipwhois.io) IP Geolocation API.

- ✅ Single and bulk IP lookups (IPv4 and IPv6)
- ✅ Works with both the **Free** and **Paid** plans
- ✅ HTTPS by default
- ✅ Localisation, field selection, threat detection, rate info
- ✅ `lookup()` / `bulkLookup()` never throw — all runtime errors returned as `success: false` maps
- ✅ No external dependencies — only the JDK standard library
- ✅ Java 11+

## Installation

### Maven

```xml
<dependency>
    <groupId>io.ipwhois</groupId>
    <artifactId>ipwhois-java</artifactId>
    <version>1.2.0</version>
</dependency>
```

### Gradle

```kotlin
implementation("io.ipwhois:ipwhois-java:1.2.0")
```

## Free vs Paid plan

The same `IPWhois` class is used for both plans. The only difference is whether
you pass an API key:

- **Free plan** — create the client **without arguments**. No API key, no
  signup required. Suitable for low-traffic and non-commercial use.
- **Paid plan** — create the client **with your API key** from
  <https://ipwhois.io>. Higher limits, plus access to bulk lookups and
  threat-detection data.

```java
IPWhois free = new IPWhois();               // Free plan — no API key
IPWhois paid = new IPWhois("YOUR_API_KEY"); // Paid plan — with API key
```

A `null`, empty, or whitespace-only key is normalised to the free plan, so
`new IPWhois("")` is equivalent to `new IPWhois()`.

Everything else (`lookup()`, options, error handling) is identical.

## Quick start — Free plan (no API key)

```java
import io.ipwhois.IPWhois;
import java.util.Map;

IPWhois ipwhois = new IPWhois(); // no API key

Map<String, Object> info = ipwhois.lookup("8.8.8.8");

@SuppressWarnings("unchecked")
Map<String, Object> flag = (Map<String, Object>) info.get("flag");

System.out.println(info.get("country") + " " + flag.get("emoji"));
// → United States 🇺🇸

System.out.println(info.get("city") + ", " + info.get("region"));
// → Mountain View, California
```

## Quick start — Paid plan (with API key)

Get an API key at <https://ipwhois.io> and pass it to the constructor:

```java
import io.ipwhois.IPWhois;
import java.util.Map;

IPWhois ipwhois = new IPWhois("YOUR_API_KEY"); // with API key

Map<String, Object> info = ipwhois.lookup("8.8.8.8");

@SuppressWarnings("unchecked")
Map<String, Object> flag = (Map<String, Object>) info.get("flag");

System.out.println(info.get("country") + " " + flag.get("emoji"));
// → United States 🇺🇸

System.out.println(info.get("city") + ", " + info.get("region"));
// → Mountain View, California
```

> ℹ️ Pass nothing to look up your own public IP: `ipwhois.lookup();` — works
> on both plans.

## Lookup options

Every option below can be passed per call, or set once on the client as a
default.

| Option       | Type            | Plans needed         | Description                                                            |
| ------------ | --------------- | -------------------- | ---------------------------------------------------------------------- |
| `lang`       | `String`        | Free + Paid          | One of: `en`, `ru`, `de`, `es`, `pt-BR`, `fr`, `zh-CN`, `ja`           |
| `fields`     | `List<String>`  | Free + Paid          | Restrict the response to specific fields (e.g. `List.of("country", "city")`)  |
| `rate`       | `boolean`       | Basic and above      | Include the `rate` block (`limit`, `remaining`)                        |
| `security`   | `boolean`       | Business and above   | Include the `security` block (proxy/vpn/tor/hosting)                   |

### Setting defaults once

Every option can be passed two ways: **per call** (as the second argument to
`lookup()` / `bulkLookup()`) or **once as a default** on the client. Per-call
options always override the defaults, so it's safe to set sensible defaults
and only override what differs for a specific call.

Defaults are set with fluent setters — `setLanguage()`, `setFields()`,
`setSecurity()`, `setRate()`, `setTimeout()`, `setConnectTimeout()`,
`setUserAgent()` — and can be chained:

```java
import io.ipwhois.IPWhois;
import java.util.List;

// Free plan
IPWhois ipwhois = new IPWhois()
        .setLanguage("en")
        .setFields(List.of("success", "country", "city", "flag.emoji"))
        .setTimeout(8);
```

```java
import io.ipwhois.IPWhois;
import java.util.List;

// Paid plan
IPWhois ipwhois = new IPWhois("YOUR_API_KEY")
        .setLanguage("en")
        .setFields(List.of("success", "country", "city", "flag.emoji"))
        .setTimeout(8);
```

Either client behaves the same way at call time — per-call options always
win over the defaults:

```java
ipwhois.lookup("8.8.8.8");                                  // uses lang=en, the field whitelist, and timeout=8
ipwhois.lookup("1.1.1.1", Map.of("lang", "de"));            // overrides lang for this single call only
```

> ⚠️ When you restrict fields with `setFields()` (or the per-call `"fields"`
> option), the API only returns the fields you ask for. Always include
> `"success"` in the list if you rely on `info.get("success")` for error
> checking — otherwise the field will be missing on responses.

> ℹ️ `setSecurity(true)` requires Business+ and `setRate(true)` requires
> Basic+. See the table above for what's available where.

> ⚠️ Setters and the constructor validate their input and throw
> `IllegalArgumentException` for clearly invalid values — `null` where it
> isn't allowed (`setLanguage`, `setFields`, `setUserAgent`) and non-positive
> seconds (`setTimeout`, `setConnectTimeout`). This is fail-fast: programmer
> errors surface at the point they happen, not silently on the next network
> call. The same validation applies to the equivalent keys in the
> constructor's options map (`timeout`, `connect_timeout`, `user_agent`).

## HTTPS Encryption

By default, all requests are sent over HTTPS. If you need to disable it (for
example, in environments without an up-to-date CA bundle), pass `"ssl"` set
to `false` to the constructor:

```java
import io.ipwhois.IPWhois;
import java.util.Map;

// Free plan
IPWhois ipwhois = new IPWhois(null, Map.of("ssl", false));
```

```java
import io.ipwhois.IPWhois;
import java.util.Map;

// Paid plan
IPWhois ipwhois = new IPWhois("YOUR_API_KEY", Map.of("ssl", false));
```

> ℹ️ HTTPS is strongly recommended for production traffic — your API key is
> sent in the query string and would otherwise travel in clear text.

## Bulk lookup (Paid plan only)

The bulk endpoint sends **up to 100 IPs** in a single GET request. Each
address counts as one credit. Available on the **Business** and **Unlimited**
plans.

```java
import io.ipwhois.BulkResult;
import io.ipwhois.IPWhois;
import java.util.List;
import java.util.Map;

IPWhois ipwhois = new IPWhois("YOUR_API_KEY");

BulkResult bulk = ipwhois.bulkLookup(List.of(
        "8.8.8.8",
        "1.1.1.1",
        "208.67.222.222",
        "2c0f:fb50:4003::"   // IPv6 is fine — mix freely
));

if (!bulk.isSuccess()) {
    // Whole-batch failure (network down, bad API key, …).
    System.err.println("Bulk failed: " + bulk.getError().get("message"));
    return;
}

for (Map<String, Object> row : bulk.getResults()) {
    if (Boolean.FALSE.equals(row.get("success"))) {
        // Per-IP errors (e.g. "Invalid IP address") are returned inline,
        // they do NOT throw — the rest of the batch is still usable.
        System.out.println("skip " + row.get("ip") + ": " + row.get("message"));
        continue;
    }
    System.out.println(row.get("ip") + " → " + row.get("country"));
}
```

> ℹ️ Bulk requires an API key. Calling `bulkLookup()` without one will fail
> at the API level.

`BulkResult` exposes the two possible outcomes of a bulk call — a list of
per-IP maps on success, or a single whole-batch error map on failure —
through `isSuccess()`, `getResults()`, and `getError()`. Just like the rest
of the library, it never throws.

## Error handling

**`lookup()` and `bulkLookup()` never throw.** Every runtime failure —
invalid IP, bad API key, rate limit, network outage, malformed JSON response —
comes back inside the response map with `success: false`
and a `message`. Just check `info.get("success")` after every call:

```java
Map<String, Object> info = ipwhois.lookup("8.8.8.8");

if (Boolean.FALSE.equals(info.get("success"))) {
    System.err.println("Lookup failed: " + info.get("message"));
    return;
}

System.out.println(info.get("country"));
```

This means an outage of the ipwhois.io API (or of your server's DNS,
connection, etc.) will never surface as an exception in your application —
you decide how to react.

The only methods that may throw are the **setters and the constructor**, and
only on clearly invalid programmer input (`IllegalArgumentException` for
`null` where it isn't allowed or non-positive timeouts). Those checks fail
fast on construction rather than letting a bad value silently break a later
lookup.

### Error response fields

Every error response contains `success: false`, a human-readable `message`,
and an `error_type` so you can branch on the category of the failure. Some
errors include extra fields you can branch on:

| Field          | When it's present                                                                            |
| -------------- | -------------------------------------------------------------------------------------------- |
| `success`      | Always — false for error responses (true for successful responses)                           |
| `message`      | Always — human-readable description of what went wrong                                       |
| `error_type`   | Always — one of `"api"`, `"network"`, `"environment"`, or `"invalid_argument"`               |
| `http_status`  | On HTTP 4xx / 5xx responses                                                                  |
| `retry_after`  | On HTTP 429 — **free plan only** (the paid endpoint does not send a `Retry-After` header)    |

```java
Map<String, Object> info = ipwhois.lookup("8.8.8.8");

if (Boolean.FALSE.equals(info.get("success"))) {
    if (Integer.valueOf(429).equals(info.get("http_status"))) {
        int wait = (Integer) info.getOrDefault("retry_after", 60);
        Thread.sleep(wait * 1000L);
        // …retry
    }
    if ("network".equals(info.get("error_type"))) {
        // DNS failure, connection refused, timeout, …
    }
    System.err.println("Error: " + info.get("message"));
    return;
}
```

## Response shape

A successful response includes (depending on your plan and selected options):

```jsonc
{
    "ip": "8.8.4.4",
    "success": true,
    "type": "IPv4",
    "continent": "North America",
    "continent_code": "NA",
    "country": "United States",
    "country_code": "US",
    "region": "California",
    "region_code": "CA",
    "city": "Mountain View",
    "latitude": 37.3860517,
    "longitude": -122.0838511,
    "is_eu": false,
    "postal": "94039",
    "calling_code": "1",
    "capital": "Washington D.C.",
    "borders": "CA,MX",
    "flag": {
        "img": "https://cdn.ipwhois.io/flags/us.svg",
        "emoji": "🇺🇸",
        "emoji_unicode": "U+1F1FA U+1F1F8"
    },
    "connection": {
        "asn": 15169,
        "org": "Google LLC",
        "isp": "Google LLC",
        "domain": "google.com"
    },
    "timezone": {
        "id": "America/Los_Angeles",
        "abbr": "PDT",
        "is_dst": true,
        "offset": -25200,
        "utc": "-07:00",
        "current_time": "2026-05-08T14:31:48-07:00"
    },
    "currency": {
        "name": "US Dollar",
        "code": "USD",
        "symbol": "$",
        "plural": "US dollars",
        "exchange_rate": 1
    },
    "security": {
        "anonymous": false,
        "proxy": false,
        "vpn": false,
        "tor": false,
        "hosting": false
    },
    "rate": {
        "limit": 250000,
        "remaining": 50155
    }
}
```

Nested objects (`flag`, `connection`, `timezone`, `currency`, `security`,
`rate`) are returned as `Map<String, Object>` and can be cast as such:

```java
@SuppressWarnings("unchecked")
Map<String, Object> conn = (Map<String, Object>) info.get("connection");
System.out.println(conn.get("isp")); // → Google LLC
```

For the full field reference, see the [official documentation](https://ipwhois.io/documentation).

An **error** response looks like:

```jsonc
{
    "success": false,
    "message": "Rate limit exceeded",
    "error_type": "api",       // 'api' / 'network' / 'environment' / 'invalid_argument'
    "http_status": 429,         // present for HTTP 4xx / 5xx
    "retry_after": 60       // additionally present on HTTP 429 — free plan only
}
```

## Requirements

- Java **11** or newer

## Build from source

```bash
git clone https://github.com/IPWhois/ipwhois-java.git
cd ipwhois-java
mvn test                  # run the test suite
mvn package               # produce target/ipwhois-java-1.2.0.jar
mvn install               # install into the local Maven repository
```

The build has no runtime dependencies; the only test-scope dependency is
JUnit 5, fetched automatically by Maven.

## Contributing

Issues and pull requests are welcome on
[GitHub](https://github.com/IPWhois/ipwhois-java).

## License

[MIT](LICENSE) © ipwhois.io
