package cfucli.relay;

import module java.base;

/** Field names for the per-session hash on the relay. Everything here is public to anyone holding
 *  the database token, so it carries routing and liveness only - never command text, never output,
 *  and never anything derived from the password beyond a MAC nobody can invert. */
public final class Meta {

    public static final String
            VERSION = "v",
            HOST_PUBLIC_KEY = "hpub",
            HOST_TAG = "htag",
            VIEWER_PUBLIC_KEY = "vpub",
            VIEWER_TAG = "vtag",
            STARTED_AT = "started",
            LAST_BEAT = "beat",
            HOST_NAME = "hostname",
            SHELL = "shell",
            LOCKED = "locked",
            VIEW_ONLY = "viewonly",
            ENDED = "ended";

    public static final String PROTOCOL_VERSION = "1";

    public static final Duration TTL = Duration.ofMinutes(10), HEARTBEAT = Duration.ofSeconds(30),
            STREAM_TTL = Duration.ofHours(6);

    public static Instant instant(Map<String, String> meta, String field) {
        var v = meta.get(field);
        if (v == null || v.isBlank()) return null;
        try {
            return Instant.ofEpochMilli(Long.parseLong(v.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean flag(Map<String, String> meta, String field) {
        return "1".equals(meta.get(field)) || "true".equalsIgnoreCase(String.valueOf(meta.get(field)));
    }

    public static String stamp(Instant i) {
        return Long.toString(i.toEpochMilli());
    }

    private Meta() {}
}
