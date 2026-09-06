package cfucli.cli;

import module java.base;
import cfucli.relay.Ids;
import picocli.CommandLine.*;

/** A shell on this machine, in a window a person can watch, that an agent drives from here.
 *  <p>
 *  This is the case an agent's own built-in console handles badly: output floods the transcript,
 *  the human cannot see it or type into it, stdin cannot be supplied, and attaching to it from
 *  outside is awkward. One of these is the opposite of all four - the window is visible and
 *  interactive, exec gives back an exact exit code with the streams apart, send-keys types into
 *  the same shell, and everything that happened is queryable afterwards.
 *  <p>
 *  Nothing here touches Upstash. No credentials are read, nothing is announced, nothing is
 *  metered; the session lives and dies on the loopback. */
@Command(name = "console", description = "Open a shell on this machine in a window, for this cli to drive. No relay.")
public final class ConsoleCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Option(names = "--shell", description = "shell to run (default: from settings)")
    String shell;

    @Option(names = "--cwd", description = "working directory for the shell")
    String cwd;

    @Option(names = "--headless", description = "no window - useful where there is no display, but then nothing can be watched and screen has nothing to show")
    boolean headless;

    @Option(names = "--relay", description = "also announce this session on the relay, so a remote viewer could join it")
    boolean relay;

    @Override
    public Integer call() {
        if (Launch.nodeRunning(opts.node)) return already();
        var jar = headless ? Optional.<Path>empty() : AppJar.find();
        if (!headless && jar.isEmpty()) {
            Out.err("cfucli: no window jar found, opening a headless console instead - " + AppJar.whereItLooked());
        }
        var client = jar.isPresent() ? viaWindow(jar.get()) : viaNode();
        var r = client.call("status", Map.of());
        if (opts.json) {
            Out.json(r);
            return 0;
        }
        report(r, jar.isPresent());
        return 0;
    }

    cfucli.node.NodeClient viaWindow(Path jar) {
        var extra = new ArrayList<String>();
        if (!relay) extra.add("--local");
        if (shell != null) {
            extra.add("--shell");
            extra.add(shell);
        }
        Launch.app(jar, opts.node, extra);
        var client = Launch.awaitNode(opts.node, Launch.UP);
        Launch.awaitHostSession(client, Launch.SESSION);
        return client;
    }

    cfucli.node.NodeClient viaNode() {
        Launch.headless(opts.node);
        var client = Launch.awaitNode(opts.node, Launch.UP);
        var args = new LinkedHashMap<String, Object>();
        args.put("local", !relay);
        if (shell != null) args.put("shell", shell);
        if (cwd != null) args.put("cwd", cwd);
        client.call("host", args, Duration.ofMinutes(2));
        return client;
    }

    Integer already() {
        var r = opts.existing().call("status", Map.of());
        if (opts.json) {
            Out.json(r);
            return 0;
        }
        if (r.hasNonNull("host")) {
            Out.line("node '" + opts.node + "' is already a console - " + r.path("host").path("shell").asText());
            drive();
            return 0;
        }
        Out.err("cfucli: node '" + opts.node + "' is running but is not hosting a shell."
                + " Pick another name with --node, or stop it with: cfucli node stop --node " + opts.node);
        return Exits.REFUSED;
    }

    void report(com.fasterxml.jackson.databind.JsonNode r, boolean windowed) {
        var host = r.path("host");
        Out.line("console '" + opts.node + "' is up - " + host.path("shell").asText()
                 + (windowed ? " in a window" : " headless")
                 + (host.path("localOnly").asBoolean() ? ", on this machine only"
                    : ", and announced on the relay so a remote viewer could join it"));
        Out.line("");
        Out.line("  node       " + opts.node + "   pid " + r.path("pid").asLong());
        Out.line("  session    " + Ids.prettySessionId(host.path("sessionId").asText()));
        Out.line("  transport  " + (host.path("localOnly").asBoolean() ? "local loopback only - the relay is not involved"
                : "local loopback and " + r.path("transport").asText()));
        if (!windowed) {
            Out.line("  screen     nothing renders it - a headless node has no emulator, so use tail");
        }
        Out.line("");
        drive();
    }

    void drive() {
        var n = " --node " + opts.node;
        Out.line("Drive it:");
        Out.line("  cfucli exec" + n + " \"dir\"            run a command; its exit code becomes this one's");
        Out.line("  cfucli send-keys" + n + " \"cd ..\"      type into the shell, as a person would");
        Out.line("  cfucli screen" + n + "                  what is on the screen right now");
        Out.line("  cfucli tail" + n + " -n 40              the last of what it printed");
        Out.line("  cfucli summary" + n + "                 what has happened, in one page");
        Out.line("Stop it:");
        Out.line("  cfucli node stop" + n);
    }
}
