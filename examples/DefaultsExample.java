import io.ipwhois.IPWhois;

import java.util.List;
import java.util.Map;

/**
 * If you make many requests with the same options, set them once on the
 * client. Per-call options always override the defaults.
 */
public final class DefaultsExample {

    public static void main(String[] args) {
        IPWhois ipwhois = new IPWhois("YOUR_API_KEY")
                .setLanguage("en")
                .setFields(List.of("success", "country", "city", "flag.emoji", "connection.isp"))
                .setSecurity(true)
                .setTimeout(8);

        // Both calls below will use lang=en, the field whitelist, and security=1.
        for (String ip : List.of("8.8.8.8", "1.1.1.1")) {
            Map<String, Object> info = ipwhois.lookup(ip);
            if (Boolean.FALSE.equals(info.get("success"))) {
                System.err.printf("%s: %s%n", ip, info.getOrDefault("message", "error"));
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> flag = (Map<String, Object>) info.getOrDefault("flag", Map.of());

            System.out.printf("%s: %s / %s %s%n",
                    ip,
                    info.get("country"),
                    info.get("city"),
                    flag.getOrDefault("emoji", "")
            );
        }

        // One-off override — this single call uses German instead of English.
        Map<String, Object> info = ipwhois.lookup("8.8.4.4", Map.of("lang", "de"));
        if (Boolean.TRUE.equals(info.get("success"))) {
            System.out.printf("8.8.4.4 (de): %s / %s%n", info.get("country"), info.get("city"));
        }
    }
}
