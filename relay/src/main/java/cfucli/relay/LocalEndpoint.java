package cfucli.relay;

import module java.base;
import com.fasterxml.jackson.databind.ObjectMapper;

/** How the end next door is found: a small file per live local session, the same trick the node
 *  already uses for its own port. No discovery protocol, no broadcast, nothing to configure.
 *  <p>
 *  The token in it is not what admits a viewer - the one-time password still is, and the payloads
 *  are encrypted either way. It only holds the loopback door shut against any other process that
 *  happens to be running, which on a machine that has had malware on it once is worth the five
 *  lines. Reading the file is the same privilege as reading everything else this tool writes. */
public record LocalEndpoint(String sessionId, int port, long pid, String node, long startedAt, String token) {

    static final ObjectMapper JSON = new ObjectMapper();
    static final String PREFIX = "local-", SUFFIX = ".json";

    public static Path path(String sessionId) {
        return Home.subdir("run").resolve(PREFIX + sessionId + SUFFIX);
    }

    public static String newToken() {
        var raw = new byte[24];
        new SecureRandom().nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public void write() {
        try {
            JSON.writeValue(path(sessionId).toFile(), Map.of("sessionId", sessionId, "port", port,
                    "pid", pid, "node", node, "startedAt", startedAt, "token", token));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Optional<LocalEndpoint> read(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? Optional.empty() : of(path(sessionId));
    }

    /** Every local session running on this machine right now, so a viewer can be pointed at one
     *  without anybody having to read an id aloud. Sweeps as it goes: a process killed outright
     *  leaves its file behind, and the honest thing is to clear it rather than list a door nobody
     *  is standing behind. */
    public static List<LocalEndpoint> live() {
        try (var files = Files.list(Home.subdir("run"))) {
            var all = files.filter(p -> p.getFileName().toString().startsWith(PREFIX))
                    .map(LocalEndpoint::of)
                    .flatMap(Optional::stream)
                    .toList();
            all.stream().filter(e -> !e.processAlive()).forEach(e -> remove(e.sessionId()));
            return all.stream().filter(LocalEndpoint::processAlive)
                    .sorted(Comparator.comparingLong(LocalEndpoint::startedAt).reversed())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    static Optional<LocalEndpoint> of(Path p) {
        if (!Files.exists(p)) return Optional.empty();
        try {
            var m = JSON.readTree(p.toFile());
            return Optional.of(new LocalEndpoint(m.get("sessionId").asText(), m.get("port").asInt(),
                    m.get("pid").asLong(), m.path("node").asText(""), m.path("startedAt").asLong(0),
                    m.path("token").asText("")));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static void remove(String sessionId) {
        try {
            Files.deleteIfExists(path(sessionId));
        } catch (IOException ignored) {
        }
    }

    public boolean processAlive() {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }
}
