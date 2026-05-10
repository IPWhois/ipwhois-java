import io.ipwhois.BulkResult;
import io.ipwhois.IPWhois;

import java.util.List;
import java.util.Map;

/**
 * Bulk lookup is available on the Business and Unlimited plans only.
 * The library uses the GET / comma-separated form of the bulk endpoint:
 *
 *     https://ipwhois.pro/bulk/IP1,IP2,IP3?key=...
 *
 * Up to 100 IP addresses can be passed in a single call. Each address
 * counts as one credit.
 */
public final class BulkExample {

    public static void main(String[] args) {
        IPWhois ipwhois = new IPWhois("YOUR_API_KEY");

        List<String> ips = List.of(
                "8.8.8.8",
                "1.1.1.1",
                "208.67.222.222",
                "2c0f:fb50:4003::"   // IPv6 is fine too — mix freely
        );

        BulkResult bulk = ipwhois.bulkLookup(ips, Map.of(
                "lang",     "en",
                "security", true
        ));

        // Whole-batch failure (network down, bad API key, rate limit, …) —
        // BulkResult.isSuccess() is false and getError() contains the error map.
        if (!bulk.isSuccess()) {
            Map<String, Object> error = bulk.getError();
            System.err.printf("Bulk request failed: %s (HTTP %s)%n",
                    error.getOrDefault("message", "unknown"),
                    error.getOrDefault("http_status", 0)
            );
            System.exit(1);
        }

        for (Map<String, Object> row : bulk.getResults()) {
            if (Boolean.FALSE.equals(row.get("success"))) {
                // Per-IP errors (e.g. "Invalid IP address", "Reserved range")
                // are returned inline. The rest of the batch is still usable.
                System.out.printf("[skip] %s — %s%n",
                        row.getOrDefault("ip", "?"),
                        row.getOrDefault("message", "error"));
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> flag = (Map<String, Object>) row.getOrDefault("flag", Map.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> conn = (Map<String, Object>) row.getOrDefault("connection", Map.of());

            System.out.printf("%-18s %s %-15s %s%n",
                    row.get("ip"),
                    flag.getOrDefault("emoji", "  "),
                    row.getOrDefault("country_code", ""),
                    conn.getOrDefault("isp", "")
            );
        }
    }
}
