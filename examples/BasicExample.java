import io.ipwhois.IPWhois;

import java.util.List;
import java.util.Map;

/**
 * Compile from the project root:
 *
 *     mvn -q -DskipTests package
 *     javac -cp target/ipwhois-java-1.2.0.jar -d examples-out examples/BasicExample.java
 *     java  -cp target/ipwhois-java-1.2.0.jar:examples-out BasicExample
 */
public final class BasicExample {

    public static void main(String[] args) {
        /* -----------------------------------------------------------------
         * 1) Free plan — no API key, ~1 request/second per client IP.
         * -------------------------------------------------------------- */
        IPWhois ipwhois = new IPWhois();

        Map<String, Object> info = ipwhois.lookup("8.8.8.8");

        // All errors — invalid IP, network failure, bad options, … — come
        // back here with success == Boolean.FALSE. The library never throws.
        if (Boolean.FALSE.equals(info.get("success"))) {
            System.err.printf("Lookup failed: %s%n", info.getOrDefault("message", "unknown"));
            System.exit(1);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> flag = (Map<String, Object>) info.getOrDefault("flag", Map.of());
        System.out.printf("%s  %s  (%s, %s)%n",
                info.get("ip"),
                flag.getOrDefault("emoji", ""),
                info.getOrDefault("country", "unknown"),
                info.getOrDefault("city", "unknown")
        );

        /* -----------------------------------------------------------------
         * 2) Look up the caller's own IP — pass nothing (or null).
         * -------------------------------------------------------------- */
        Map<String, Object> me = ipwhois.lookup();
        if (Boolean.TRUE.equals(me.get("success"))) {
            System.out.printf("My IP: %s — %s%n", me.get("ip"), me.get("country"));
        }

        /* -----------------------------------------------------------------
         * 3) Paid plan — supply the API key.
         * -------------------------------------------------------------- */
        IPWhois paid = new IPWhois("YOUR_API_KEY");

        Map<String, Object> result = paid.lookup("1.1.1.1", Map.of(
                "lang",     "en",                                                       // localised country/city/…
                "fields",   List.of("success", "country", "city", "connection.isp", "flag.emoji"),
                "security", true,                                                       // include proxy/vpn/tor flags
                "rate",     true                                                        // include rate-limit info
        ));

        System.out.println(result);
    }
}
