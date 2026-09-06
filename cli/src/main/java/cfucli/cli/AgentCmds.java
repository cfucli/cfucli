package cfucli.cli;

import module java.base;
import cfucli.relay.Ids;
import picocli.CommandLine.*;

/** The three verbs an agent needs that a human rarely types: pick up a detached job, look at the
 *  screen instead of the transcript, and find out what is already running on this machine. */
@Command(name = "wait", description = "Wait for a detached job and return its exit code as this command's.")
final class WaitCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Parameters(index = "0", description = "the job id exec --detach printed")
    String jobId;

    @Option(names = {"-t", "--timeout"}, description = "seconds to wait (default: ${DEFAULT-VALUE})")
    int timeoutSeconds = 300;

    @Option(names = "--quiet", description = "print nothing; the exit code is the answer")
    boolean quiet;

    @Override
    public Integer call() {
        var r = opts.existing().call("wait", Map.of("jobId", jobId, "waitMs", Duration.ofSeconds(timeoutSeconds).toMillis()),
                Duration.ofSeconds(timeoutSeconds + 30L));
        if (opts.json) {
            Out.json(r);
            return ExecCmd.exitOf(r);
        }
        if (!quiet) {
            var stdout = r.path("stdout").asText("");
            var stderr = r.path("stderr").asText("");
            if (!stdout.isEmpty()) System.out.print(stdout);
            if (!stderr.isEmpty()) System.err.print(stderr);
        }
        if (r.has("refused")) Out.err("cfucli: the far end refused it - " + r.path("refused").asText());
        else if (!"ok".equals(r.path("state").asText())) {
            Out.err("cfucli: " + r.path("state").asText() + " after " + Out.millis(r.path("millis").asLong()));
        }
        return ExecCmd.exitOf(r);
    }
}

/** The screen as rendered, not the transcript. What an agent should look at first: forty lines
 *  that say where the shell actually is, rather than every byte it has ever printed. */
@Command(name = "screen", description = "The terminal screen as it looks right now.")
final class ScreenCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Override
    public Integer call() {
        var r = opts.existing().call("screen", Map.of());
        if (opts.json) {
            Out.json(r);
            return r.path("hasScreen").asBoolean() ? 0 : 5;
        }
        if (!r.path("hasScreen").asBoolean()) {
            Out.err("cfucli: " + r.path("detail").asText());
            return 5;
        }
        System.out.print(r.path("screen").asText());
        System.out.println();
        return 0;
    }
}

/** Read straight from the endpoint files rather than through a node. Asking a node would mean
 *  starting one to answer "is anything running?", which is both absurd and untrue by the time it
 *  answers. This also sweeps the files left behind by anything that was killed outright. */
@Command(name = "local", description = "Sessions hosted on this machine right now, which need no relay to join.")
final class LocalCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Override
    public Integer call() {
        var live = cfucli.relay.LocalEndpoint.live();
        if (opts.json) {
            var out = cfucli.node.Wire.JSON.createArrayNode();
            for (var e : live) {
                out.addObject().put("sessionId", e.sessionId()).put("node", e.node())
                        .put("pid", e.pid()).put("port", e.port()).put("startedAt", e.startedAt());
            }
            Out.json(out);
            return 0;
        }
        if (live.isEmpty()) {
            Out.line("no sessions are hosted on this machine");
            return 0;
        }
        Out.line(Out.pad("session", 14) + Out.pad("node", 14) + Out.pad("pid", 8) + Out.pad("port", 7) + "started");
        for (var e : live) {
            Out.line(Out.pad(Ids.prettySessionId(e.sessionId()), 14)
                     + Out.pad(e.node() == null || e.node().isBlank() ? "-" : e.node(), 14)
                     + Out.pad(String.valueOf(e.pid()), 8)
                     + Out.pad(String.valueOf(e.port()), 7)
                     + Out.time(e.startedAt()));
        }
        return 0;
    }
}
