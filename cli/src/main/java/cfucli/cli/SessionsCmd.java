package cfucli.cli;

import module java.base;
import picocli.CommandLine.*;

@Command(name = "sessions", description = "Sessions this node has recorded, newest first.")
public final class SessionsCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Option(names = "--limit") int limit = 25;

    @Override
    public Integer call() {
        var r = opts.client().call("sessions", Map.of("limit", limit));
        if (opts.json) {
            Out.json(r);
            return 0;
        }
        if (r.isEmpty()) {
            Out.line("this node has recorded no sessions");
            return 0;
        }
        Out.line(Out.pad("session", 12) + Out.pad("role", 8) + Out.pad("started", 10)
                 + Out.pad("state", 8) + Out.pad("host", 18) + "shell");
        for (var s : r) {
            Out.line(Out.pad(s.path("sessionId").asText(), 12)
                     + Out.pad(s.path("role").asText(), 8)
                     + Out.pad(Out.time(s.path("startedAt").asLong()), 10)
                     + Out.pad(s.path("live").asBoolean() ? "live" : "ended", 8)
                     + Out.pad(s.path("hostName").asText("-"), 18)
                     + s.path("shell").asText("-"));
        }
        return 0;
    }
}
