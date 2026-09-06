package cfucli.node;

import module java.base;
import cfucli.record.RecordDb;
import cfucli.relay.SettingsStore;

/** The resident half, headless. Started by the cli when none is running, or by hand for a look
 *  at what it prints. */
public final class Node {

    public static void main(String[] args) {
        var name = arg(args, "--node", RecordDb.DEFAULT_NODE);
        var existing = NodeInfo.read(name).filter(NodeInfo::processAlive);
        if (existing.isPresent()) {
            System.err.println("[node] " + name + " already running on port " + existing.get().port()
                               + " (pid " + existing.get().pid() + ")");
            System.exit(3);
        }
        var settings = SettingsStore.load();
        var service = new NodeService(name, settings);
        var server = NodeServer.start(name, service);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "node-shutdown"));
        System.out.println("[node] " + name + " listening on 127.0.0.1:" + server.port()
                           + "  store " + RecordDb.open(name).location());
        server.awaitShutdown();
        System.out.println("[node] " + name + " stopped");
        // ArcadeDB, pty4j and JNA all keep non-daemon threads that outlive a close, so returning
        // from main is not the same as the process ending. A node that says stopped and stays in
        // the task manager still owns its name to anyone reading the port file. The window role
        // has the same guard for the same reason.
        System.exit(0);
    }

    static String arg(String[] args, String flag, String fallback) {
        for (var i = 0; i < args.length - 1; i++) if (flag.equals(args[i])) return args[i + 1];
        return fallback;
    }

    private Node() {}
}
