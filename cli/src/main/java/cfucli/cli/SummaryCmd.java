package cfucli.cli;

import module java.base;
import picocli.CommandLine.*;

@Command(name = "summary", description = "The whole session at a glance, without reading any of it.")
public final class SummaryCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Option(names = "--session") String session;

    @Override
    public Integer call() {
        var args = new LinkedHashMap<String, Object>();
        if (session != null) args.put("sessionId", session);
        var r = opts.client().call("summary", args);
        if (opts.json) {
            Out.json(r);
            return 0;
        }
        Out.line("session   " + r.path("sessionId").asText() + "  as " + r.path("role").asText()
                 + (r.path("live").asBoolean() ? "  (live)" : "  (ended: " + r.path("endReason").asText("-") + ")"));
        Out.line("host      " + r.path("hostName").asText("-") + "   shell " + r.path("shell").asText("-")
                 + "   via " + r.path("transport").asText("-"));
        Out.line("ran       " + Out.time(r.path("startedAt").asLong())
                 + " for " + Out.millis(r.path("millis").asLong()));
        Out.line("recorded  " + r.path("events").asLong() + " chunks, " + Out.bytes(r.path("totalBytes").asLong())
                 + (r.path("redactedEvents").asLong() > 0 ? "  (" + r.path("redactedEvents").asLong() + " redacted)" : ""));
        Out.line("commands  " + r.path("jobs").asLong() + " run, " + r.path("failedJobs").asLong() + " failed");
        var byStream = r.path("eventsByStream");
        var names = new ArrayList<String>();
        byStream.fieldNames().forEachRemaining(names::add);
        for (var s : names) {
            Out.line("  " + Out.pad(s, 14) + Out.pad(byStream.path(s).asLong() + " chunks", 16)
                     + Out.bytes(r.path("bytesByStream").path(s).asLong()));
        }
        return 0;
    }
}
