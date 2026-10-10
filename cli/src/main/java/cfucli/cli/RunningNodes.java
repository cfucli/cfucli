package cfucli.cli;

import module java.base;
import cfucli.node.NodeClient;
import cfucli.node.NodeInfo;
import cfucli.relay.Home;

/** Stops the nodes running on this machine before cfucli.exe is replaced. A node still holding the old code keeps
 *  answering with it; measured: a jar replaced under a running node fails later with NoSuchMethodError, not at once.
 *  A port file outlives a node that was killed outright, so only a live process is stopped. */
final class RunningNodes {
    private RunningNodes() {}

    /** The names of the nodes it stopped. */
    static List<String> stopAll() {
        var stopped = new ArrayList<String>();
        try (var files = Files.list(Home.subdir("run"))) {
            for (var f : files.sorted().toList()) {
                var name = f.getFileName().toString();
                if (!name.startsWith("node-") || !name.endsWith(".json")) continue;
                var node = name.substring("node-".length(), name.length() - ".json".length());
                var info = NodeInfo.read(node).filter(NodeInfo::processAlive);
                if (info.isEmpty()) continue;
                try {
                    new NodeClient(node).call("shutdown", Map.of(), Duration.ofSeconds(5));
                } catch (RuntimeException e) {
                    ProcessHandle.of(info.get().pid()).ifPresent(ProcessHandle::destroy);
                }
                ProcessHandle.of(info.get().pid()).ifPresent(p -> {
                    try {
                        p.onExit().get(10, TimeUnit.SECONDS);
                    } catch (Exception ignored) {
                    }
                });
                stopped.add(node);
            }
        } catch (IOException ignored) {
        }
        return stopped;
    }
}
