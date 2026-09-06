package cfucli.cli;

import module java.base;
import picocli.CommandLine.*;

/** The high-level index: what ran, how long it took, how much it produced, how it ended. Read
 *  this before reading any output - it is usually enough to know where to look. */
@Command(name = "jobs", description = "Every command run through exec in this session, with what it cost.")
public final class JobsCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Option(names = "--failed", description = "only the ones that did not exit zero")
    boolean failed;

    @Option(names = "--exit", description = "only jobs with this exit code")
    Integer exitCode;

    @Option(names = "--limit") int limit = 200;

    @Option(names = "--session") String session;

    @Override
    public Integer call() {
        var args = new LinkedHashMap<String, Object>();
        args.put("failed", failed);
        args.put("limit", limit);
        if (exitCode != null) args.put("exitCode", exitCode);
        if (session != null) args.put("sessionId", session);
        var r = opts.client().call("jobs", args);
        if (opts.json) {
            Out.json(r);
            return 0;
        }
        if (r.isEmpty()) {
            Out.line("no commands have been run through exec in this session");
            return 0;
        }
        Out.line(Out.pad("job", 14) + Out.pad("start", 10) + Out.pad("took", 9)
                 + Out.pad("exit", 9) + Out.pad("out", 7) + Out.pad("err", 7) + "command");
        for (var j : r) {
            Out.line(Out.pad(j.path("jobId").asText(), 14)
                     + Out.pad(Out.time(j.path("startedAt").asLong()), 10)
                     + Out.pad(j.has("millis") ? Out.millis(j.path("millis").asLong()) : "running", 9)
                     + Out.pad(j.hasNonNull("exitCode") ? j.path("exitCode").asText() : j.path("state").asText(), 9)
                     + Out.pad(Out.bytes(j.path("stdoutBytes").asLong()), 7)
                     + Out.pad(Out.bytes(j.path("stderrBytes").asLong()), 7)
                     + Out.oneLine(j.path("command").asText(), 60));
        }
        return 0;
    }
}
