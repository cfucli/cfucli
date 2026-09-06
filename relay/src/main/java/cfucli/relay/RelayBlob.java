package cfucli.relay;

import module java.base;


/** Pulls the three rendezvous values out of whatever the person actually pasted.
 *  <p>
 *  Deliberately forgiving about the shape, because the input is a human copying from an Upstash
 *  console tab, or from another machine's settings.toml, or out of an email. Any line carrying a
 *  recognised key and a value is taken; everything else is ignored rather than refused. The keys
 *  match the aliases SettingsStore already accepts, so a settings.toml can be pasted whole. */
public final class RelayBlob {

    public static final String REDIS = "REDIS_URL", REST_URL = "UPSTASH_REDIS_REST_URL",
            REST_TOKEN = "UPSTASH_REDIS_REST_TOKEN";

    static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("redis_url", REDIS), Map.entry("redisurl", REDIS), Map.entry("native_url", REDIS),
            Map.entry("upstash_redis_url", REDIS),
            Map.entry("upstash_redis_rest_url", REST_URL), Map.entry("rest_url", REST_URL),
            Map.entry("resturl", REST_URL),
            Map.entry("upstash_redis_rest_token", REST_TOKEN), Map.entry("rest_token", REST_TOKEN),
            Map.entry("resttoken", REST_TOKEN));

    /** Insertion-ordered so the report back lists them in the order they were read. */
    public static Map<String, String> parse(String text) {
        var out = new LinkedHashMap<String, String>();
        for (var raw : text.split("\\R")) {
            var line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
            var eq = line.indexOf('=');
            if (eq < 0) eq = line.indexOf(':');
            if (eq <= 0) continue;
            var key = ALIASES.get(line.substring(0, eq).strip().toLowerCase(Locale.ROOT).replace('-', '_'));
            if (key == null) continue;
            var value = unquote(line.substring(eq + 1).strip());
            if (!value.isEmpty()) out.put(key, value);
        }
        return out;
    }

    /** A pasted TOML line arrives quoted and a pasted console line does not, so accept both. */
    static String unquote(String v) {
        if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'")))
            return v.substring(1, v.length() - 1);
        return v;
    }

    public static void apply(Settings s, String key, String value) {
        switch (key) {
            case REDIS -> s.redisUrl(value);
            case REST_URL -> s.restUrl(value);
            case REST_TOKEN -> s.restToken(value);
            default -> throw new IllegalArgumentException("not a rendezvous key: " + key);
        }
    }

    private RelayBlob() {}
}
