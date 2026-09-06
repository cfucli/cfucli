package cfucli.cli;

import module java.base;
import picocli.CommandLine.*;

/** Types into the live shared shell, exactly as if a person had. Use this when the point is to
 *  drive the shell someone is watching; use exec when the point is to know what happened. */
@Command(name = "send-keys", description = "Type into the live shared shell (no exit code, no stream separation).")
public final class SendKeysCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Parameters(index = "0", arity = "0..*", description = "text to type")
    List<String> text;

    @Option(names = "--no-enter", description = "do not append a carriage return")
    boolean noEnter;

    /** Interrupting something is half of what driving a shell by hand is for, and a control
     *  character cannot be typed onto a command line to be passed through as text. */
    @Option(names = "--ctrl", description = "send a control character instead of text: --ctrl c for Ctrl-C, --ctrl d for Ctrl-D")
    String ctrl;

    /** A one-shot cli call already costs a JVM start, so it can afford to wait long enough to
     *  learn that the far end said no. The window covers a relay round trip on either transport. */
    @Option(names = "--confirm-ms", description = "how long to wait for a refusal (default: ${DEFAULT-VALUE})")
    long confirmMs = 1500;

    @Override
    public Integer call() {
        var payload = payload();
        var r = opts.existing().call("keys", Map.of("text", payload, "confirmMs", confirmMs));
        if (opts.json) {
            Out.json(r);
        } else if (r.has("refused")) {
            Out.err("cfucli: the far end refused it - " + r.path("refused").asText());
        }
        return r.path("accepted").asBoolean(true) ? Exits.OK : Exits.REFUSED;
    }

    String payload() {
        if (ctrl != null && !ctrl.isBlank()) {
            var c = Character.toUpperCase(ctrl.strip().charAt(0));
            if (c < 'A' || c > 'Z') throw new IllegalArgumentException("--ctrl takes a letter, as in --ctrl c");
            return String.valueOf((char) (c - 'A' + 1));
        }
        if (text == null || text.isEmpty()) throw new IllegalArgumentException("send-keys needs text, or --ctrl <letter>");
        return String.join(" ", text) + (noEnter ? "" : "\r");
    }
}
