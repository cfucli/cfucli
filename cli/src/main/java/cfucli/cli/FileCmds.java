package cfucli.cli;

import module java.base;
import com.fasterxml.jackson.databind.JsonNode;
import picocli.CommandLine.*;

/** Moving a file to or from the far machine. Both are the joining end's verbs, and both say which
 *  of the three routes was actually taken - the loopback when the far end turns out to be this
 *  machine, the relay for something small, a folder both machines have mounted for something big. */
@Command(name = "put", description = "Send a file to the far machine.")
final class PutCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Parameters(index = "0", description = "the local file to send")
    String local;

    @Parameters(index = "1", arity = "0..1", description = "where to put it on the far machine; a directory is allowed (default: that machine's home directory)")
    String remote;

    @Mixin TransferOpts how;

    @Override
    public Integer call() {
        var args = new LinkedHashMap<String, Object>();
        args.put("local", local);
        if (remote != null) args.put("remote", remote);
        return Transfers.run(opts, how, "put", args, local);
    }
}

@Command(name = "get", description = "Fetch a file from the far machine.")
final class GetCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Parameters(index = "0", description = "the file on the far machine")
    String remote;

    @Parameters(index = "1", arity = "0..1", description = "where to put it here; a directory is allowed (default: this machine's home directory)")
    String local;

    @Mixin TransferOpts how;

    @Override
    public Integer call() {
        var args = new LinkedHashMap<String, Object>();
        args.put("remote", remote);
        if (local != null) args.put("local", local);
        return Transfers.run(opts, how, "get", args, remote);
    }
}

final class TransferOpts {

    @Option(names = "--via", description = "route to use: auto, relay, shared, same-machine (default: ${DEFAULT-VALUE})")
    String via = "auto";

    @Option(names = "--force", description = "replace the file at the far side if one is already there")
    boolean force;

    @Option(names = {"-t", "--timeout"}, description = "seconds to allow (default: ${DEFAULT-VALUE}); a large file over a synced folder may need more")
    int timeoutSeconds = 300;
}

final class Transfers {

    static Integer run(CommonOpts opts, TransferOpts how, String verb, Map<String, Object> args, String what) {
        args.put("via", how.via);
        args.put("force", how.force);
        args.put("waitMs", Duration.ofSeconds(how.timeoutSeconds).toMillis());
        var r = opts.existing().call(verb, args, Duration.ofSeconds(how.timeoutSeconds + 30L));
        if (opts.json) {
            Out.json(r);
            return r.path("ok").asBoolean() ? Exits.OK : Exits.REFUSED;
        }
        if (!r.path("ok").asBoolean()) {
            Out.err("cfucli: " + what + " - " + r.path("error").asText("it did not complete"));
            return Exits.REFUSED;
        }
        Out.line(what + " -> " + r.path("path").asText() + "   " + Out.bytes(r.path("bytes").asLong())
                 + " over " + route(r) + " in " + Out.millis(r.path("millis").asLong()));
        return 0;
    }

    static String route(JsonNode r) {
        var route = r.path("route").asText("");
        return route.isBlank() ? "the session" : route;
    }

    private Transfers() {}
}
