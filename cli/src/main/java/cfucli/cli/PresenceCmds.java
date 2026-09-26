package cfucli.cli;

import module java.base;
import picocli.CommandLine.*;

/** Discover who is around and ask instead of being handed a session id and a password to read out
 *  loud. Being "available" only publishes that you exist and can be asked - it is not consent to
 *  any particular request, which is why {@code approve}/{@code decline} are separate verbs run by
 *  whoever is actually there to answer. */
@Command(name = "available", description = "Turn discoverability on or off, so others can send a connect request instead of being given an id and password.")
final class AvailableCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Parameters(index = "0", arity = "0..1", description = "on or off (default: ${DEFAULT-VALUE})")
    String value = "on";

    @Option(names = "--as", description = "the name others will see and request you by; saved, so it is only needed once")
    String as;

    @Override
    public Integer call() {
        var on = !value.equalsIgnoreCase("off") && !value.equals("0") && !value.equalsIgnoreCase("false");
        var args = new LinkedHashMap<String, Object>();
        args.put("value", on);
        if (as != null) args.put("identity", as);
        var r = opts.client().call("available", args, Duration.ofSeconds(30));
        if (opts.json) {
            Out.json(r);
            return 0;
        }
        Out.line(on ? "Available as \"" + r.path("identityName").asText() + "\" - others can now 'cfucli request "
                + r.path("identityName").asText() + "'." : "No longer available for requests.");
        return 0;
    }
}

@Command(name = "online", description = "List who is currently available for a connect request.")
final class OnlineCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Override
    public Integer call() {
        var r = opts.client().call("online", Map.of(), Duration.ofSeconds(30));
        if (opts.json) {
            Out.json(r);
            return 0;
        }
        if (!r.elements().hasNext()) {
            Out.line("Nobody is available right now.");
            return 0;
        }
        for (var e : r) {
            Out.line(e.path("identity").asText() + "  (" + e.path("node").asText() + ", seen "
                    + e.path("lastSeenSecondsAgo").asLong() + "s ago)");
        }
        return 0;
    }
}

@Command(name = "request", description = "Ask someone who is online to let you connect - waits for them to approve, then joins automatically.")
final class RequestCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Parameters(index = "0", description = "their identity name, as shown by 'online'")
    String identity;

    @Option(names = {"-t", "--timeout"}, description = "seconds to wait for them to answer (default: ${DEFAULT-VALUE})")
    int timeoutSeconds = 90;

    @Override
    public Integer call() {
        var waitMs = Duration.ofSeconds(timeoutSeconds).toMillis();
        var r = opts.client().call("request", Map.of("identity", identity, "waitMs", waitMs),
                Duration.ofSeconds(timeoutSeconds + 10L));
        if (opts.json) {
            Out.json(r);
            return 0;
        }
        var status = r.path("status");
        Out.line(identity + " approved. Joined " + r.path("sessionId").asText() + " - " + status.path("detail").asText());
        if (status.hasNonNull("hostName")) {
            Out.line("Host " + status.path("hostName").asText() + " running " + status.path("shell").asText("a shell"));
        }
        return status.path("usable").asBoolean(false) ? 0 : 4;
    }
}

@Command(name = "requests", description = "List connect requests waiting for you to answer.")
final class RequestsCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Override
    public Integer call() {
        var r = opts.client().call("requests", Map.of(), Duration.ofSeconds(30));
        if (opts.json) {
            Out.json(r);
            return 0;
        }
        if (!r.elements().hasNext()) {
            Out.line("Nothing pending.");
            return 0;
        }
        for (var e : r) {
            Out.line(e.path("requesterIdentity").asText() + "  requestId=" + e.path("requestId").asText()
                    + "  from " + e.path("fromNode").asText() + ", " + e.path("secondsAgo").asLong() + "s ago");
        }
        Out.line("");
        Out.line("cfucli approve <requestId>   or   cfucli decline <requestId>");
        return 0;
    }
}

@Command(name = "approve", description = "Accept a pending connect request: starts hosting and hands them the session.")
final class ApproveCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Parameters(index = "0", description = "the request id, from 'requests'")
    String requestId;

    @Option(names = "--shell", description = "shell to run (default: from settings)")
    String shell;

    @Option(names = "--cwd", description = "working directory for the shell")
    String cwd;

    @Override
    public Integer call() {
        var args = new LinkedHashMap<String, Object>();
        args.put("requestId", requestId);
        if (shell != null) args.put("shell", shell);
        if (cwd != null) args.put("cwd", cwd);
        var r = opts.client().call("approve", args, Duration.ofMinutes(2));
        if (opts.json) {
            Out.json(r);
            return 0;
        }
        Out.line("Approved " + r.path("requesterIdentity").asText() + " - they are joining now.");
        Out.line("Stop sharing with:  cfucli end --node " + opts.node);
        return 0;
    }
}

@Command(name = "decline", description = "Refuse a pending connect request.")
final class DeclineCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Parameters(index = "0", description = "the request id, from 'requests'")
    String requestId;

    @Option(names = "--reason", description = "shown to the requester")
    String reason;

    @Override
    public Integer call() {
        var args = new LinkedHashMap<String, Object>();
        args.put("requestId", requestId);
        if (reason != null) args.put("reason", reason);
        var r = opts.client().call("decline", args, Duration.ofSeconds(30));
        if (opts.json) {
            Out.json(r);
            return 0;
        }
        Out.line("Declined " + r.path("requesterIdentity").asText() + ".");
        return 0;
    }
}
