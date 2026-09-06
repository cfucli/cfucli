package cfucli.cli;

import module java.base;
import cfucli.node.NodeClient;
import cfucli.node.NodeInfo;
import picocli.CommandLine.*;

@Command(name = "node", description = "Start or stop the resident half by hand.",
        subcommands = {NodeStartCmd.class, NodeStopCmd.class})
public final class NodeCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Override
    public Integer call() {
        var info = NodeInfo.read(opts.node);
        if (info.isEmpty() || !info.get().processAlive()) {
            Out.line("node '" + opts.node + "' is not running");
            return 5;
        }
        Out.line("node '" + opts.node + "' on 127.0.0.1:" + info.get().port() + "  pid " + info.get().pid());
        return 0;
    }
}

@Command(name = "start", description = "Start a node without starting a session.")
final class NodeStartCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Override
    public Integer call() {
        var c = new NodeClient(opts.node);
        if (c.running()) {
            Out.line("node '" + opts.node + "' is already running");
            return 0;
        }
        c.ensureRunning(null, Self.jar());
        Out.line("node '" + opts.node + "' started on port " + NodeInfo.read(opts.node).orElseThrow().port());
        return 0;
    }
}

@Command(name = "stop", description = "Stop a node, ending any session it holds.")
final class NodeStopCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Override
    public Integer call() {
        var c = new NodeClient(opts.node);
        if (!c.running()) {
            Out.line("node '" + opts.node + "' is not running");
            return 0;
        }
        c.call("shutdown", Map.of());
        Out.line("node '" + opts.node + "' stopping");
        return 0;
    }
}
