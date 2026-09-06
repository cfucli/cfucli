package cfucli.cli;

import module java.base;
import picocli.CommandLine.*;

@Command(name = "grep", description = "Search everything the session has said, with context.")
public final class GrepCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Parameters(index = "0", description = "regular expression")
    String regex;

    @Option(names = {"-i", "--ignore-case"}) boolean ignoreCase;

    @Option(names = {"-C", "--context"}, description = "lines of context either side")
    int context = 0;

    @Option(names = "--streams", description = "all | out | err | in | control (comma separated)")
    String streams;

    @Option(names = "--limit", description = "maximum matches (default: ${DEFAULT-VALUE})")
    int limit = 200;

    @Option(names = "--session") String session;

    @Override
    public Integer call() {
        var args = new LinkedHashMap<String, Object>();
        args.put("regex", regex);
        args.put("ignoreCase", ignoreCase);
        args.put("context", context);
        args.put("limit", limit);
        if (streams != null) args.put("streams", streams);
        if (session != null) args.put("sessionId", session);
        var r = opts.client().call("grep", args);
        if (opts.json) {
            Out.json(r);
            return r.isEmpty() ? 1 : 0;
        }
        for (var m : r) {
            for (var b : m.path("before")) Out.line("  " + Out.pad(m.path("line").asLong() + "-", 8) + b.asText());
            Out.line("> " + Out.pad(m.path("line").asLong() + ":", 8) + m.path("text").asText()
                     + "   [" + m.path("stream").asText() + " " + Out.time(m.path("ts").asLong()) + "]");
            for (var a : m.path("after")) Out.line("  " + Out.pad(m.path("line").asLong() + "+", 8) + a.asText());
        }
        if (r.isEmpty()) {
            Out.line("no match");
            return 1;
        }
        return 0;
    }
}
