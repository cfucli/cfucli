package cfucli.cli;

import module java.base;
import picocli.CommandLine.*;

@Command(name = "events", description = "A slice of the recording by sequence number.")
final class EventsCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Option(names = "--from") Long from;
    @Option(names = "--to") Long to;
    @Option(names = "--streams") String streams;
    @Option(names = "--limit") int limit = 500;
    @Option(names = "--session") String session;
    @Option(names = "--raw", description = "keep terminal escape sequences") boolean raw;

    /** --limit takes the first N from where the slice starts; what you almost always want when
     *  looking at a session that is still running is the last N, the same as tail -n. */
    @Option(names = {"-n", "--lines"}, description = "print only the last N of them")
    int lines;

    @Override
    public Integer call() {
        var args = new LinkedHashMap<String, Object>();
        if (from != null) args.put("from", from);
        if (to != null) args.put("to", to);
        if (streams != null) args.put("streams", streams);
        if (session != null) args.put("sessionId", session);
        args.put("limit", limit);
        var r = opts.client().call("events", args);
        if (opts.json) {
            Out.json(r);
            return 0;
        }
        var all = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        r.forEach(all::add);
        var start = lines > 0 && all.size() > lines ? all.size() - lines : 0;
        for (var i = start; i < all.size(); i++) {
            var e = all.get(i);
            Out.line("#" + Out.pad(e.path("seq").asText(), 7) + Out.pad(e.path("stream").asText(), 13)
                     + Out.pad(Out.time(e.path("ts").asLong()), 10)
                     + Out.oneLine(raw ? e.path("text").asText() : e.path("plain").asText(), 100));
        }
        return 0;
    }
}

@Command(name = "forget", description = "Delete a session's recording entirely.")
final class ForgetCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Option(names = "--session", description = "session id (default: the one on this node)")
    String session;

    @Override
    public Integer call() {
        var args = new LinkedHashMap<String, Object>();
        if (session != null) args.put("sessionId", session);
        var r = opts.client().call("forget", args);
        if (opts.json) Out.json(r);
        else Out.line("removed " + r.path("eventsRemoved").asLong() + " recorded chunks");
        return 0;
    }
}

@Command(name = "scrub", description = "Blank a session's recorded text but keep its shape and exit codes.")
final class ScrubCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Option(names = "--session") String session;

    @Override
    public Integer call() {
        var args = new LinkedHashMap<String, Object>();
        if (session != null) args.put("sessionId", session);
        var r = opts.client().call("scrub", args);
        if (opts.json) Out.json(r);
        else Out.line("blanked " + r.path("eventsScrubbed").asLong() + " recorded chunks");
        return 0;
    }
}

@Command(name = "retain", description = "Drop recordings older than the retention window.")
final class RetainCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Option(names = "--days", description = "keep this many days (default: from settings)")
    Integer days;

    @Override
    public Integer call() {
        var args = new LinkedHashMap<String, Object>();
        if (days != null) args.put("days", days);
        var r = opts.client().call("retain", args);
        if (opts.json) Out.json(r);
        else Out.line("removed " + r.path("sessionsRemoved").asInt() + " sessions older than "
                      + r.path("days").asInt() + " days");
        return 0;
    }
}
