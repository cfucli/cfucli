package cfucli.relay;

import module java.base;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;

/** Reads ~/cfucli/settings.toml. Credential keys are accepted under the names
 *  Upstash's own console uses, because that is what a person pastes, and under camelCase, because
 *  that is what this tool writes back. */
public final class SettingsStore {

    public static final String FILE = "settings.toml";

    static final Map<String, List<String>> ALIASES = Map.of(
            "redisUrl", List.of("redis_url", "redisurl", "native_url"),
            "restUrl", List.of("upstash_redis_rest_url", "rest_url", "resturl"),
            "restToken", List.of("upstash_redis_rest_token", "rest_token", "resttoken"));

    /** The live file is always the one under the user's home, because the tool writes back to it -
     *  font size, the last session joined - and a distribution folder may sit somewhere read-only
     *  or shared. But a machine that has never run this has no such file, and without credentials
     *  a host cannot announce itself on the relay at all: the far end would come up local-only and
     *  nothing off that machine could reach it. So on first run the copy shipped NEXT TO THE JAR
     *  seeds the home one. That means a distribution folder is self-contained and the person on the
     *  far machine has nothing to place by hand. */
    public static Path path() {
        var home = Home.file(FILE);
        if (!Files.exists(home)) seed(home);
        return home;
    }

    /** Copies the shipped settings alongside the running jar into the home location, once. Any
     *  failure here is deliberately silent: a missing seed is the normal case for a developer
     *  running from classes, and it leaves the tool exactly as it was before - working, with no
     *  credentials. */
    static void seed(Path home) {
        try {
            var src = SettingsStore.class.getProtectionDomain().getCodeSource();
            if (src == null) return;
            var jar = Paths.get(src.getLocation().toURI());
            if (!Files.isRegularFile(jar)) return;
            var shipped = jar.getParent().resolve(FILE);
            if (Files.exists(shipped)) Files.copy(shipped, home);
        } catch (Exception ignored) {
            // nothing to seed from, or nowhere to write it - carry on unconfigured
        }
    }

    public static Settings load() {
        var raw = readRaw();
        var s = new Settings();
        s.redisUrl(str(raw, "redisUrl"));
        s.restUrl(str(raw, "restUrl"));
        s.restToken(str(raw, "restToken"));
        var pref = str(raw, "transportPreference");
        if (pref != null) s.transportPreference(pref);
        var shell = str(raw, "defaultShell");
        if (shell != null) s.defaultShell(shell);
        var idle = num(raw, "idleTimeoutMinutes");
        if (idle != null) s.idleTimeoutMinutes(idle.intValue());
        var max = num(raw, "maxSessionHours");
        if (max != null) s.maxSessionHours(max.intValue());
        var keep = num(raw, "logRetentionDays");
        if (keep != null) s.logRetentionDays(keep.intValue());
        var rec = raw.get("recordoutput");
        if (rec != null) s.recordOutput(Boolean.parseBoolean(String.valueOf(rec)));
        s.largeFileExchangeDir(str(raw, "largeFileExchangeDir"));
        var thr = num(raw, "largeFileThresholdBytes");
        if (thr != null) s.largeFileThresholdBytes(thr.longValue());
        var font = num(raw, "terminalFontSize");
        if (font != null) s.terminalFontSize(font.doubleValue());
        s.appJar(str(raw, "appJar"));
        return s;
    }

    /** Writes one field back without disturbing the rest, which matters because the file holds the
     *  credentials a person pasted in by hand. */
    public static void update(java.util.function.Consumer<Settings> change) {
        var s = load();
        change.accept(s);
        save(s);
    }

    public static void save(Settings s) {
        var lines = new ArrayList<String>();
        lines.add("# cfucli settings. Credentials come from the Upstash console:");
        lines.add("# Redis tab for REDIS_URL, REST tab for the two UPSTASH_REDIS_REST_* values.");
        lines.add("");
        put(lines, "REDIS_URL", s.redisUrl());
        put(lines, "UPSTASH_REDIS_REST_URL", s.restUrl());
        put(lines, "UPSTASH_REDIS_REST_TOKEN", s.restToken());
        lines.add("");
        put(lines, "transportPreference", s.transportPreference());
        put(lines, "defaultShell", s.defaultShell());
        put(lines, "idleTimeoutMinutes", s.idleTimeoutMinutes());
        put(lines, "maxSessionHours", s.maxSessionHours());
        put(lines, "logRetentionDays", s.logRetentionDays());
        put(lines, "recordOutput", s.recordOutput());
        put(lines, "largeFileExchangeDir", s.largeFileExchangeDir());
        put(lines, "largeFileThresholdBytes", s.largeFileThresholdBytes());
        put(lines, "terminalFontSize", s.terminalFontSize());
        put(lines, "appJar", s.appJar());
        try {
            Files.writeString(path(), String.join(System.lineSeparator(), lines) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + path(), e);
        }
    }

    static void put(List<String> lines, String key, Object value) {
        if (value == null) return;
        var v = value instanceof String s ? "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : String.valueOf(value);
        lines.add(key + " = " + v);
    }

    static Map<String, Object> readRaw() {
        var p = path();
        if (!Files.exists(p)) return Map.of();
        try {
            var tree = new TomlMapper().readValue(p.toFile(), Map.class);
            var flat = new LinkedHashMap<String, Object>();
            ((Map<?, ?>) tree).forEach((k, v) -> flat.put(String.valueOf(k).toLowerCase(), v));
            return flat;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + p + " - " + advice(e), e);
        }
    }

    /** A malformed settings file stops every node on the machine from starting, so the reason has to
     *  be in the first line rather than in a stack trace. The trap that is actually hit: a Windows
     *  path pasted into a double-quoted TOML value, where every backslash is an escape. TOML's
     *  single-quoted literal string needs no escaping at all, which is the fix to suggest. */
    static String advice(Exception e) {
        var m = e.getMessage() == null ? "" : e.getMessage();
        if (m.contains("escape")) {
            return "a backslash in a double-quoted value is an escape character in TOML."
                   + " Use single quotes for Windows paths: largeFileExchangeDir = 'C:\\path\\to\\folder'. (" + m + ")";
        }
        return m;
    }

    static String str(Map<String, Object> raw, String field) {
        var v = lookup(raw, field);
        return v == null ? null : String.valueOf(v).trim();
    }

    static Number num(Map<String, Object> raw, String field) {
        var v = lookup(raw, field);
        if (v instanceof Number n) return n;
        if (v == null) return null;
        try {
            return Long.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Object lookup(Map<String, Object> raw, String field) {
        var direct = raw.get(field.toLowerCase());
        if (direct != null) return direct;
        for (var alias : ALIASES.getOrDefault(field, List.of())) {
            var v = raw.get(alias);
            if (v != null) return v;
        }
        return null;
    }

    private SettingsStore() {}
}
