package cfucli.cli;

import module java.base;
import picocli.CommandLine.*;

@Command(name = "host", description = "Share this machine's shell and print the id and one-time password to read out.")
public final class HostCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Option(names = "--shell", description = "shell to run (default: from settings)")
    String shell;

    @Option(names = "--cwd", description = "working directory for the shell")
    String cwd;

    @Option(names = "--local", description = "do not announce this on the relay at all - only something on this machine can join it")
    boolean local;

    @Override
    public Integer call() {
        var args = new LinkedHashMap<String, Object>();
        if (shell != null) args.put("shell", shell);
        if (cwd != null) args.put("cwd", cwd);
        if (local) args.put("local", true);
        var r = opts.client().call("host", args, Duration.ofMinutes(2));
        if (opts.json) {
            Out.json(r);
            return 0;
        }
        Out.line("Sharing " + r.path("shell").asText() + " over " + r.path("transport").asText());
        Out.line("");
        Out.line("  session id : " + r.path("prettyId").asText());
        Out.line("  password   : " + r.path("prettyPassword").asText());
        Out.line("");
        if (r.path("local").asBoolean()) {
            Out.line("Nothing left this machine. Only something running here can join, and no relay command was spent.");
        } else {
            Out.line("Read those two to the person joining. The password works once, for this session only.");
        }
        Out.line("Stop sharing with:  cfucli end --node " + opts.node);
        return 0;
    }
}
