package cfucli.cli;

import module java.base;
import picocli.CommandLine.*;

@Command(name = "job", description = "One job in full: what ran, how it ended, and its output.")
public final class JobCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Parameters(index = "0", description = "job id")
    String jobId;

    @Option(names = "--session", description = "only look in this session; without it, any session in this node's store")
    String session;

    @Override
    public Integer call() {
        var args = new LinkedHashMap<String, Object>();
        args.put("jobId", jobId);
        if (session != null) args.put("sessionId", session);
        var r = opts.client().call("job", args);
        if (opts.json) {
            Out.json(r);
            return 0;
        }
        Out.line("command   " + r.path("command").asText());
        Out.line("asked by  " + r.path("origin").asText() + "   started " + Out.time(r.path("startedAt").asLong()));
        Out.line("ended     " + r.path("state").asText()
                 + (r.hasNonNull("exitCode") ? " exit=" + r.path("exitCode").asInt() : "")
                 + (r.has("millis") ? " in " + Out.millis(r.path("millis").asLong()) : ""));
        var stdout = r.path("stdout").asText("");
        var stderr = r.path("stderr").asText("");
        if (!stdout.isEmpty()) {
            Out.line("--- stdout ---");
            System.out.print(stdout);
            if (!stdout.endsWith("\n")) System.out.println();
        }
        if (!stderr.isEmpty()) {
            Out.line("--- stderr ---");
            System.out.print(stderr);
            if (!stderr.endsWith("\n")) System.out.println();
        }
        return 0;
    }
}
