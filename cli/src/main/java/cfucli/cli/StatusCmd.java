package cfucli.cli;

import module java.base;
import picocli.CommandLine.*;

@Command(name = "status", description = "What this node is doing, and whether the far end is answering.")
public final class StatusCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Option(names = "--show-password", description = "also print this host's one-time password, to read out again")
    boolean showPassword;

    @Override
    public Integer call() {
        var c = opts.existing();
        if (!c.running()) {
            if (opts.json) {
                Out.json(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                        .put("node", opts.node).put("running", false));
            } else {
                Out.line("node '" + opts.node + "' is not running");
            }
            return 5;
        }
        var r = c.call("status", Map.of("showPassword", showPassword));
        if (opts.json) {
            Out.json(r);
            return 0;
        }
        Out.line("node      " + r.path("node").asText() + "  pid " + r.path("pid").asLong());
        Out.line("transport " + r.path("transport").asText());
        for (var note : r.path("transportNotes")) Out.line("          " + note.asText());
        Out.line("store     " + r.path("store").asText());
        var host = r.path("host");
        if (host.isNull() || host.isMissingNode()) {
            Out.line("host      not sharing");
        } else {
            Out.line("host      " + host.path("prettyId").asText()
                     + (host.path("connected").asBoolean() ? "  viewer connected" : "  waiting for a viewer")
                     + (host.path("locked").asBoolean() ? "  LOCKED" : "")
                     + (host.path("viewOnly").asBoolean() ? "  VIEW-ONLY" : "")
                     + "  shell " + host.path("shell").asText()
                     + (host.path("shellAlive").asBoolean() ? "" : " (exited)"));
            if (host.has("prettyPassword")) Out.line("          password " + host.path("prettyPassword").asText());
        }
        var viewer = r.path("viewer");
        if (viewer.isNull() || viewer.isMissingNode()) {
            Out.line("viewer    not connected");
        } else {
            Out.line("viewer    " + viewer.path("sessionId").asText() + "  " + viewer.path("state").asText()
                     + " - " + viewer.path("detail").asText());
            if (viewer.has("lastRefusal")) Out.line("          last refusal: " + viewer.path("lastRefusal").asText());
        }
        return 0;
    }
}
