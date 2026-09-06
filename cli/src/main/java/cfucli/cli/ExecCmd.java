package cfucli.cli;

import module java.base;
import com.fasterxml.jackson.databind.JsonNode;
import picocli.CommandLine.*;

/** Runs a command and returns its exit code as this process's exit code. stdout goes to stdout,
 *  stderr to stderr - separated, because deciding what happened from one merged blob is guesswork.
 *  <p>
 *  Where it runs depends on what the node is. A node viewing a session runs it on the far machine;
 *  a node hosting one runs it here, which is the local-console case. Either way the command is
 *  echoed into the shared view, so the human watching the window sees what the agent did. */
@Command(name = "exec", description = "Run a command in this session; its exit code becomes this command's exit code.")
public final class ExecCmd implements Callable<Integer> {

    /** Enough for any sane command's output and small enough not to bury a transcript. What is cut
     *  is never lost - it is recorded, and the note on stderr says how to read the rest. */
    public static final int DEFAULT_MAX_BYTES = 200_000;

    @Mixin CommonOpts opts;

    @Parameters(index = "0", arity = "1..*", description = "the command to run")
    List<String> command;

    @Option(names = "--cwd", description = "working directory")
    String cwd;

    @Option(names = {"-t", "--timeout"}, description = "seconds to allow (default: ${DEFAULT-VALUE})")
    int timeoutSeconds = 120;

    @Option(names = "--detach", description = "return a job id immediately instead of waiting; pick it up with wait or job")
    boolean detach;

    @Option(names = "--stdin", description = "text to write to the command's stdin, then close it")
    String stdin;

    @Option(names = "--stdin-file", description = "file to write to the command's stdin; - reads this process's stdin")
    String stdinFile;

    @Option(names = "--max-bytes", description = "truncate each stream at this many bytes, 0 for all of it (default: ${DEFAULT-VALUE})")
    int maxBytes = DEFAULT_MAX_BYTES;

    @Option(names = "--quiet", description = "print nothing; the exit code is the answer")
    boolean quiet;

    @Override
    public Integer call() {
        var args = new LinkedHashMap<String, Object>();
        args.put("command", String.join(" ", command));
        args.put("timeoutMs", Duration.ofSeconds(timeoutSeconds).toMillis());
        args.put("detach", detach);
        if (cwd != null) args.put("cwd", cwd);
        var input = input();
        if (input != null) args.put("stdin", input);
        var r = opts.existing().call("exec", args, Duration.ofSeconds(timeoutSeconds + 30L));
        if (opts.json) {
            Out.json(r);
            return detach ? 0 : exitOf(r);
        }
        if (detach) {
            Out.line(r.path("jobId").asText());
            return 0;
        }
        if (!quiet) {
            emit(System.out, r.path("stdout").asText(""), "stdout", r);
            emit(System.err, r.path("stderr").asText(""), "stderr", r);
        }
        if (r.has("refused")) Out.err("cfucli: the far end refused it - " + r.path("refused").asText());
        else if (!"ok".equals(r.path("state").asText())) {
            Out.err("cfucli: " + r.path("state").asText() + " after " + Out.millis(r.path("millis").asLong()));
        }
        return exitOf(r);
    }

    void emit(PrintStream to, String text, String which, JsonNode r) {
        if (text.isEmpty()) return;
        if (maxBytes <= 0 || text.length() <= maxBytes) {
            to.print(text);
            return;
        }
        to.print(text.substring(0, maxBytes));
        to.flush();
        Out.err("cfucli: " + which + " cut at " + Out.bytes(maxBytes) + " of " + Out.bytes(text.length())
                + " - all of it is recorded: cfucli job " + r.path("jobId").asText() + " --node " + opts.node);
    }

    String input() {
        if (stdin != null) return stdin;
        if (stdinFile == null) return null;
        try {
            return "-".equals(stdinFile)
                    ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(Paths.get(stdinFile), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read stdin from " + stdinFile, e);
        }
    }

    /** The command's own exit code where there is one, so a script sees what it would have seen
     *  running the command locally. A refusal has no exit code because nothing ran, and it is not
     *  an unexplained failure either - the far end understood and said no. */
    static int exitOf(JsonNode r) {
        if (r.has("refused")) return Exits.REFUSED;
        return r.hasNonNull("exitCode") ? r.get("exitCode").asInt() : Exits.FAILED;
    }
}
