package cfucli.node;

import module java.base;
import cfucli.record.Streams;
import cfucli.relay.Frame;
import cfucli.relay.FrameType;

/** Side-channel commands on the machine that owns the shell. Separate from the shared shell on
 *  purpose: an exit code taken from a process we started is exact, where one scraped out of an
 *  interactive shell is a guess that fails the moment the far end is sitting in a pager.
 *  <p>
 *  Two callers reach it. A viewer asks over the relay, which is the remote case the tool was
 *  built for; and the cli asks the node directly, which is the local case - an agent driving a
 *  window a human is watching, on one machine, with no far end at all. Both land in the same
 *  runner, are echoed into the same shared view and are recorded the same way. */
public final class HostExec implements AutoCloseable {

    final HostSession session;
    final ExecutorService jobs = Executors.newCachedThreadPool(r -> {
        var t = new Thread(r, "exec-job");
        t.setDaemon(true);
        return t;
    });
    final Map<String, Process> running = new ConcurrentHashMap<>();
    final Map<String, CompletableFuture<ExecResult>> pending = new ConcurrentHashMap<>();

    HostExec(HostSession session) {
        this.session = session;
    }

    /** From the cli, on this machine. The lock and view-only flags are deliberately not consulted:
     *  they exist to hold the FAR end out, and the person pressing lock is the same person whose
     *  agent is calling this. */
    public String submit(String command, String cwd, Duration timeout, String stdin, String origin) {
        var jobId = ExecWire.newJobId();
        session.touch();
        session.recorder.startJob(jobId, command, cwd, origin);
        session.echo("exec " + jobId + ": " + command);
        pending.put(jobId, new CompletableFuture<>());
        jobs.submit(() -> run(jobId, command, cwd, timeout, stdin));
        return jobId;
    }

    public ExecResult await(String jobId, Duration wait) {
        var f = pending.get(jobId);
        if (f == null) throw new NoSuchElementException("no job " + jobId + " is running on this node");
        try {
            return f.get(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return new ExecResult(jobId, null, ExecResult.TIMEOUT, 0, 0, wait.toMillis(), "", "");
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting for " + jobId);
        }
    }

    public ExecResult run(String command, String cwd, Duration timeout, String stdin, String origin) {
        var limit = timeout == null ? ExecRunner.DEFAULT_TIMEOUT : timeout;
        return await(submit(command, cwd, limit, stdin, origin), limit.plusSeconds(15));
    }

    /** Something is actually running here, which is what keeps an idle timer from cutting off a
     *  build nobody is typing at. */
    public boolean busy() {
        return !running.isEmpty();
    }

    public boolean cancel(String jobId) {
        var p = running.remove(jobId);
        if (p == null) return false;
        ExecRunner.kill(p);
        session.recorder.control("exec " + jobId + " cancelled");
        return true;
    }

    void request(Frame f) {
        var n = ExecWire.read(f.payload());
        var jobId = Wire.str(n, "jobId", ExecWire.newJobId());
        if (session.refuseIfRestricted("running commands")) {
            session.send(FrameType.EXEC_EXIT, ExecWire.exit(jobId, null, ExecResult.FAILED, 0, 0, 0));
            return;
        }
        var command = Wire.str(n, "command", "");
        var cwd = Wire.str(n, "cwd", null);
        var timeout = Duration.ofMillis(Wire.l(n, "timeoutMs", ExecRunner.DEFAULT_TIMEOUT.toMillis()));
        session.recorder.startJob(jobId, command, cwd, "viewer");
        session.echo("exec " + jobId + ": " + command);
        pending.put(jobId, new CompletableFuture<>());
        jobs.submit(() -> run(jobId, command, cwd, timeout, Wire.str(n, "stdin", null)));
    }

    void run(String jobId, String command, String cwd, Duration timeout, String stdin) {
        var r = ExecRunner.run(jobId, command, cwd == null ? null : Paths.get(cwd), session.pty.command(), timeout, stdin,
                (channel, text) -> {
                    var stdout = "stdout".equals(channel);
                    session.recorder.jobOutput(jobId, stdout ? Streams.EXEC_STDOUT : Streams.EXEC_STDERR, text);
                    session.send(stdout ? FrameType.EXEC_STDOUT : FrameType.EXEC_STDERR, ExecWire.chunk(jobId, text));
                },
                p -> running.put(jobId, p));
        running.remove(jobId);
        // The session was in use right up to here, so the idle clock starts again now rather than
        // from when the job was submitted - otherwise a long build is followed instantly by an
        // idle timeout.
        session.touch();
        session.recorder.finishJob(jobId, r.exitCode(), r.stdoutBytes(), r.stderrBytes(), r.state());
        session.echo("exec " + jobId + " " + r.state()
                     + (r.exitCode() == null ? "" : " exit=" + r.exitCode()) + " in " + r.millis() + "ms");
        session.send(FrameType.EXEC_EXIT, ExecWire.exit(jobId, r.exitCode(), r.state(),
                r.stdoutBytes(), r.stderrBytes(), r.millis()));
        var f = pending.get(jobId);
        if (f != null) f.complete(r);
    }

    void cancel(Frame f) {
        cancel(Wire.str(ExecWire.read(f.payload()), "jobId", ""));
    }

    @Override
    public void close() {
        jobs.shutdownNow();
        running.values().forEach(ExecRunner::kill);
        running.clear();
        pending.values().forEach(f -> f.cancel(true));
        pending.clear();
    }
}
