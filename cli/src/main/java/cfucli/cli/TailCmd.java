package cfucli.cli;

import module java.base;
import picocli.CommandLine.*;

/** The last of what the session printed, and optionally whatever it prints next.
 *  <p>
 *  Following is done by asking the node for events after the last sequence number seen, which
 *  needs no streaming protocol and cannot get stuck holding a socket open. */
@Command(name = "tail", description = "The last of what the session printed; -f follows it.")
public final class TailCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Option(names = {"-n", "--lines"}, description = "how many recorded chunks (default: ${DEFAULT-VALUE})")
    int n = 40;

    @Option(names = "--streams", description = "shell (default) | all | out | err | in | control, comma separated")
    String streams;

    @Option(names = "--session", description = "session id, when this node holds more than one")
    String session;

    @Option(names = "--raw", description = "keep terminal escape sequences")
    boolean raw;

    @Option(names = {"-f", "--follow"}, description = "keep printing as more arrives")
    boolean follow;

    @Option(names = "--for", description = "seconds to follow for, 0 until interrupted (default: ${DEFAULT-VALUE})")
    int forSeconds;

    @Option(names = "--interval-ms", description = "how often to ask while following (default: ${DEFAULT-VALUE})")
    long intervalMs = 250;

    @Override
    public Integer call() {
        var client = opts.client();
        // Named rather than left to each verb's own default, so following prints the same streams
        // the first page did instead of quietly widening once it starts.
        var wanted = streams == null ? "shell" : streams;
        var args = new LinkedHashMap<String, Object>();
        args.put("n", n);
        args.put("streams", wanted);
        if (session != null) args.put("sessionId", session);
        var r = client.call("tail", args);
        if (opts.json) {
            Out.json(r);
            return 0;
        }
        var last = -1L;
        for (var e : r) {
            System.out.print(text(e));
            last = Math.max(last, e.path("seq").asLong());
        }
        System.out.flush();
        if (!follow) {
            System.out.println();
            return 0;
        }
        var deadline = forSeconds <= 0 ? null : Instant.now().plusSeconds(forSeconds);
        while (deadline == null || Instant.now().isBefore(deadline)) {
            Launch.sleep(intervalMs);
            var more = new LinkedHashMap<String, Object>();
            more.put("from", last + 1);
            more.put("limit", 2000);
            more.put("streams", wanted);
            if (session != null) more.put("sessionId", session);
            for (var e : client.call("events", more)) {
                System.out.print(text(e));
                last = Math.max(last, e.path("seq").asLong());
            }
            System.out.flush();
        }
        System.out.println();
        return 0;
    }

    /** Records go out back to back, because reconstructing what a program drew means reproducing
     *  its bytes and not improving on them. The one exception is a note of the tool's own: it
     *  starts on a fresh line, since a shell leaves its prompt unterminated and a note appended to
     *  that prompt reads as though the shell had printed it. Nothing is inserted into the
     *  program's own streams. */
    String text(com.fasterxml.jackson.databind.JsonNode e) {
        var s = raw ? e.path("text").asText() : e.path("plain").asText();
        var note = "control".equals(e.path("stream").asText()) || "error".equals(e.path("stream").asText());
        if (note && !atLineStart) s = "\n" + s;
        if (!s.isEmpty()) atLineStart = s.endsWith("\n");
        return s;
    }

    /** Whether the last thing printed ended a line, so a note knows if it needs to break first. */
    boolean atLineStart = true;
}
