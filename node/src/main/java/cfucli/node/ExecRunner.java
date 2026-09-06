package cfucli.node;

import module java.base;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Runs one command in its own process, off to the side of the shared shell.
 *  <p>
 *  This is the whole reason exec is trustworthy: stdout, stderr and the exit code come back
 *  separately and exactly, whatever the shared shell happens to be doing - sitting in a pager,
 *  waiting at a password prompt, halfway through a line someone is typing. The transcript is
 *  echoed into the shared view afterwards so both humans still see what the agent did. */
public final class ExecRunner {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);

    public static ExecResult run(String jobId, String command, Path cwd, String shell, Duration timeout,
                                 String stdin, BiConsumer<String, String> onChunk, Consumer<Process> onStart) {
        var started = System.nanoTime();
        var out = new StringBuilder();
        var err = new StringBuilder();
        try {
            var pb = new ProcessBuilder(commandLine(shell, command));
            pb.directory((cwd == null ? Paths.get(System.getProperty("user.home")) : cwd).toFile());
            pb.redirectErrorStream(false);
            var p = pb.start();
            if (onStart != null) onStart.accept(p);
            feed(p, stdin);
            var stdout = drain(p.getInputStream(), "stdout", onChunk, out);
            var stderr = drain(p.getErrorStream(), "stderr", onChunk, err);
            var limit = timeout == null ? DEFAULT_TIMEOUT : timeout;
            var finished = p.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                kill(p);
                join(stdout, stderr);
                return result(jobId, null, ExecResult.TIMEOUT, out, err, started);
            }
            join(stdout, stderr);
            return result(jobId, p.exitValue(), ExecResult.OK, out, err, started);
        } catch (IOException e) {
            err.append(e.getMessage() == null ? e.toString() : e.getMessage());
            return result(jobId, null, ExecResult.FAILED, out, err, started);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return result(jobId, null, ExecResult.CANCELLED, out, err, started);
        }
    }

    /** Descendants first, and it matters. Every command here runs as {@code cmd /c <command>}, so
     *  destroying the process we started kills the shell and orphans whatever it launched - which
     *  then keeps the pipes open and holds the job "running" for as long as the drain threads will
     *  wait. Cancelling a ping this way took eleven seconds and collected another four hundred
     *  bytes of output from a process nobody could see any more. */
    public static void kill(Process p) {
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
    }

    /** Always closed, even when there is nothing to send. A child that reads stdin and is handed
     *  a pipe nobody ever closes waits for input that will never come, and the only symptom is a
     *  command that mysteriously runs until the timeout. */
    static void feed(Process p, String stdin) {
        try (var w = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)) {
            if (stdin != null && !stdin.isEmpty()) w.write(stdin);
        } catch (IOException ignored) {
        }
    }

    static List<String> commandLine(String shell, String command) {
        var s = shell == null ? "cmd.exe" : shell.toLowerCase();
        if (s.contains("powershell") || s.contains("pwsh")) {
            return List.of(shell, "-NoProfile", "-NonInteractive", "-Command", command);
        }
        if (s.contains("cmd")) {
            return List.of(shell, "/c", "chcp 65001>nul & " + command);
        }
        return List.of(shell, "-c", command);
    }

    static Thread drain(InputStream in, String channel, BiConsumer<String, String> onChunk, StringBuilder sink) {
        return Thread.ofPlatform().daemon().name("exec-" + channel).start(() -> {
            var buf = new char[8192];
            try (var r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                var n = 0;
                while ((n = r.read(buf)) >= 0) {
                    if (n == 0) continue;
                    var text = new String(buf, 0, n);
                    synchronized (sink) {
                        sink.append(text);
                    }
                    if (onChunk != null) onChunk.accept(channel, text);
                }
            } catch (IOException ignored) {
            }
        });
    }

    static void join(Thread... threads) {
        for (var t : threads) {
            try {
                t.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static ExecResult result(String jobId, Integer exit, String state, StringBuilder out, StringBuilder err, long startedNanos) {
        var o = out.toString();
        var e = err.toString();
        return new ExecResult(jobId, exit, state,
                o.getBytes(StandardCharsets.UTF_8).length, e.getBytes(StandardCharsets.UTF_8).length,
                (System.nanoTime() - startedNanos) / 1_000_000, o, e);
    }

    private ExecRunner() {}
}
