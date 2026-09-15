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

    /** Written via a temp file and an atomic rename. A reader polls for this file appearing, so a
     *  plain write races it: the path exists from the moment it is created, and a read landing in
     *  that window sees an empty or half-written file. */
    public void write() {
        var target = path(node);
        var tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            JSON.writeValue(tmp.toFile(), Map.of(
                    "node", node, "port", port, "pid", pid, "startedAt", startedAt));
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Optional<NodeInfo> read(String node) {
        var p = path(node);
        if (!Files.exists(p)) return Optional.empty();
        try {
            var m = JSON.readTree(p.toFile());
            // An empty file parses to a MissingNode rather than throwing, and get() on that returns
            // null - so an unguarded dereference here is an NPE, not a caught IOException.
            if (m == null || !m.hasNonNull("port") || !m.hasNonNull("pid")) return Optional.empty();
            return Optional.of(new NodeInfo(node, m.get("port").asInt(), m.get("pid").asLong(),
                    m.hasNonNull("startedAt") ? m.get("startedAt").asLong() : 0L));
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
