package cfucli.app;

import module java.base;
import cfucli.node.*;
import cfucli.relay.SettingsStore;

/** The window runs the node, rather than talking to one.
 *  <p>
 *  It has to. ArcadeDB takes an exclusive lock on the store directory, so only one process may
 *  hold a given node's history; and the taps the terminal reads are in-process handles on the pty
 *  and the relay link. Running the resident half here also has the effect the whole tool exists
 *  for: what claude-code sends through the cli lands in the same session the human is watching,
 *  because it is the same node. */
public final class NodeHost implements AutoCloseable {

    final String node;
    final NodeService service;
    final NodeServer server;

    NodeHost(String node, NodeService service, NodeServer server) {
        this.node = node;
        this.service = service;
        this.server = server;
    }

    public static NodeHost open(String node) {
        var held = NodeInfo.read(node).filter(NodeInfo::processAlive);
        if (held.isPresent()) throw new NodeBusy(held.get());
        var service = new NodeService(node, SettingsStore.load());
        var server = NodeServer.start(node, service);
        return new NodeHost(node, service, server);
    }

    /** Ends whatever holds the name, then waits for the port file to go. Only ever on an explicit
     *  instruction from the human, because it ends any session that node is serving. */
    public static void evict(NodeInfo held, Duration wait) {
        try {
            new NodeClient(held.node()).call("shutdown", Map.of(), Duration.ofSeconds(10));
        } catch (RuntimeException e) {
            ProcessHandle.of(held.pid()).ifPresent(ProcessHandle::destroy);
        }
        var deadline = Instant.now().plus(wait);
        while (Instant.now().isBefore(deadline)) {
            if (NodeInfo.read(held.node()).filter(NodeInfo::processAlive).isEmpty()) return;
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new IllegalStateException("node '" + held.node() + "' did not stop");
    }

    /** A name nobody is using, so the window can open without arguing about it. */
    public static String freeName(String preferred) {
        if (NodeInfo.read(preferred).filter(NodeInfo::processAlive).isEmpty()) return preferred;
        for (var i = 2; i < 100; i++) {
            var candidate = preferred + "-" + i;
            if (NodeInfo.read(candidate).filter(NodeInfo::processAlive).isEmpty()) return candidate;
        }
        throw new IllegalStateException("every name from " + preferred + " to " + preferred + "-99 is in use");
    }

    public String node() {
        return node;
    }

    public NodeService service() {
        return service;
    }

    public int port() {
        return server.port();
    }

    @Override
    public void close() {
        server.close();
    }
}
