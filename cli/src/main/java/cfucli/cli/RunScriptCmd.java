package cfucli.cli;

import module java.base;
import cfucli.node.NodeClient;
import picocli.CommandLine.*;

/** Run a local script on the far machine.
 *  <p>
 *  This exists because of what {@code exec} costs when the thing you want to run is more than one
 *  line. A command reaches the far shell as a string, so every layer between here and there gets a
 *  turn at its quotes - a shell, this cli, then the interpreter over there - and a PowerShell
 *  one-liner with nested quoting is the single most reliable way to waste an hour. Writing the
 *  script to a file locally sidesteps all of it, and this verb is that: put, exec, delete.
 *  <p>
 *  It is composed from the node calls that already exist rather than being a new one, so the
 *  activity log, the recording, the job index and the exit code all behave exactly as they do for
 *  {@code exec} - the job {@code jobs} shows is the interpreter running the file. */
@Command(name = "run-script", description = "Send a local script to the far machine, run it, delete it.")
public final class RunScriptCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Parameters(index = "0", description = "the local script file")
    String script;

    @Parameters(index = "1..*", arity = "0..*", description = "arguments passed to the script")
    List<String> args = List.of();

    @Option(names = "--shell", description = "interpreter: auto, powershell, cmd, bash (default: ${DEFAULT-VALUE})")
    String shell = "auto";

    @Option(names = "--cwd", description = "working directory on the far machine")
    String cwd;

    @Option(names = {"-t", "--timeout"}, description = "seconds to allow (default: ${DEFAULT-VALUE})")
    int timeoutSeconds = 300;

    @Option(names = "--keep", description = "leave the script on the far machine instead of deleting it")
    boolean keep;

    @Option(names = "--max-bytes", description = "truncate each stream at this many bytes, 0 for all of it (default: ${DEFAULT-VALUE})")
    int maxBytes = ExecCmd.DEFAULT_MAX_BYTES;

    @Option(names = "--quiet", description = "print nothing; the exit code is the answer")
    boolean quiet;

    @Override
    public Integer call() throws Exception {
        var local = Paths.get(script);
        if (!Files.isRegularFile(local)) {
            Out.err("cfucli: no such script - " + local.toAbsolutePath());
            return Exits.USAGE;
        }
        var kind = Interpreters.of(shell, local.getFileName().toString());
        if (kind == null) {
            Out.err("cfucli: cannot tell how to run " + local.getFileName()
                    + " - name it .ps1, .cmd, .bat or .sh, or pass --shell");
            return Exits.USAGE;
        }
        var client = opts.existing();
        // When this node hosts the session, both of its ends ARE this machine, so there is nothing
        // to transfer - put says so and refuses, correctly. Staging a copy of a file that is already
        // here would only invent a temporary path and a cleanup step to go with it.
        if (client.call("status", Map.of()).has("host")) {
            if (!Interpreters.nameIsUsable(kind, local.getFileName().toString())) {
                Out.err("cfucli: powershell -File only runs a file named .ps1, and nothing is staged"
                        + " when the session is on this machine - rename " + local.getFileName() + " to .ps1");
                return Exits.USAGE;
            }
            return run(client, kind, local.toAbsolutePath().toString());
        }
        var remote = send(client, kind, local);
        if (remote == null) return Exits.REFUSED;
        try {
            return run(client, kind, remote);
        } finally {
            if (!keep) remove(client, kind, remote);
        }
    }

    /** The path comes back from the answer rather than being predicted here: where a put actually
     *  landed is the far machine's business, and it is the only end that knows its home directory. */
    String send(NodeClient client, String kind, Path local) {
        var a = new LinkedHashMap<String, Object>();
        a.put("local", local.toAbsolutePath().toString());
        a.put("remote", Interpreters.stagedName(kind, local.getFileName().toString()));
        a.put("via", "auto");
        a.put("force", true);
        a.put("waitMs", Duration.ofSeconds(timeoutSeconds).toMillis());
        var r = client.call("put", a, Duration.ofSeconds(timeoutSeconds + 30L));
        if (!r.path("ok").asBoolean()) {
            Out.err("cfucli: could not send the script - " + r.path("error").asText("it did not complete"));
            return null;
        }
        return r.path("path").asText();
    }

    Integer run(NodeClient client, String kind, String remote) {
        var a = new LinkedHashMap<String, Object>();
        a.put("command", Interpreters.command(kind, remote, args));
        a.put("timeoutMs", Duration.ofSeconds(timeoutSeconds).toMillis());
        a.put("detach", false);
        a.put("maxBytes", maxBytes);
        if (cwd != null) a.put("cwd", cwd);
        var r = client.call("exec", a, Duration.ofSeconds(timeoutSeconds + 30L));
        if (opts.json) {
            Out.json(r);
            return ExecCmd.exitOf(r);
        }
        if (!quiet) {
            var out = r.path("stdout").asText("");
            var err = r.path("stderr").asText("");
            if (!out.isEmpty()) System.out.print(out.endsWith("\n") ? out : out + System.lineSeparator());
            if (!err.isEmpty()) System.err.print(err.endsWith("\n") ? err : err + System.lineSeparator());
        }
        if (r.has("refused")) Out.err("cfucli: the far end refused it - " + r.path("refused").asText());
        else if (!"ok".equals(r.path("state").asText()))
            Out.err("cfucli: " + r.path("state").asText() + " after " + Out.millis(r.path("millis").asLong()));
        return ExecCmd.exitOf(r);
    }

    /** Said out loud when it fails rather than swallowed: a staging file left behind on someone
     *  else's machine is small, but it is litter they did not ask for and cannot see. */
    void remove(NodeClient client, String kind, String remote) {
        try {
            var a = new LinkedHashMap<String, Object>();
            a.put("command", Interpreters.delete(kind, remote));
            a.put("timeoutMs", Duration.ofSeconds(30).toMillis());
            a.put("detach", false);
            var r = client.call("exec", a, Duration.ofSeconds(60));
            if (ExecCmd.exitOf(r) != 0) Out.err("cfucli: left " + remote + " on the far machine");
        } catch (RuntimeException e) {
            Out.err("cfucli: left " + remote + " on the far machine - " + e.getMessage());
        }
    }
}
