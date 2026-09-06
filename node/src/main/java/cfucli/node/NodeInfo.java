package cfucli.node;

import module java.base;
import com.fasterxml.jackson.databind.ObjectMapper;
import cfucli.relay.Home;

/** Where a running node put its loopback port. Same shape as proj's daemon: a small file the
 *  client reads, so the cli needs no configuration and no discovery protocol. */
public record NodeInfo(String node, int port, long pid, long startedAt) {

    static final ObjectMapper JSON = new ObjectMapper();

    public static Path path(String node) {
        return Home.subdir("run").resolve("node-" + node + ".json");
    }

    public void write() {
        try {
            JSON.writeValue(path(node).toFile(), Map.of(
                    "node", node, "port", port, "pid", pid, "startedAt", startedAt));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Optional<NodeInfo> read(String node) {
        var p = path(node);
        if (!Files.exists(p)) return Optional.empty();
        try {
            var m = JSON.readTree(p.toFile());
            return Optional.of(new NodeInfo(node, m.get("port").asInt(), m.get("pid").asLong(),
                    m.has("startedAt") ? m.get("startedAt").asLong() : 0L));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static void remove(String node) {
        try {
            Files.deleteIfExists(path(node));
        } catch (IOException ignored) {
        }
    }

    public boolean processAlive() {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }
}
