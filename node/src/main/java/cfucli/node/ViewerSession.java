package cfucli.node;

import module java.base;
import java.util.function.Consumer;
import com.arcadedb.database.Database;
import cfucli.record.*;
import cfucli.relay.*;

/** The end that watches and drives. Everything the far shell prints arrives here and is recorded
 *  locally, which is what makes history queryable without asking the far end anything. */
public final class ViewerSession implements AutoCloseable, FileEnd {

    final RelayTransport transport;
    final Database db;
    final String sessionId;
    final RelayLink link;
    final Recorder recorder;
    final Settings settings;
    final FileMover files = new FileMover(this);
    final List<Consumer<byte[]>> taps = new CopyOnWriteArrayList<>();
    final List<Consumer<String>> activity = new CopyOnWriteArrayList<>();
    final Map<String, CompletableFuture<ExecResult>> pending = new ConcurrentHashMap<>();
    final Map<String, StringBuilder> stdout = new ConcurrentHashMap<>(), stderr = new ConcurrentHashMap<>();
    final Map<String, Long> startedAt = new ConcurrentHashMap<>();

    final AtomicReference<String> lastRefusal = new AtomicReference<>();
    final AtomicLong lastRefusalAt = new AtomicLong(), reportedRefusalAt = new AtomicLong();
    final List<Runnable> watchers = new CopyOnWriteArrayList<>();

    volatile boolean closed;
    volatile RemoteState remote = RemoteState.unknown();
    volatile java.util.function.Supplier<String> snapshot;

    ViewerSession(RelayTransport transport, Database db, String sessionId, RelayLink link, Recorder recorder,
                  Settings settings) {
        this.transport = transport;
        this.db = db;
        this.sessionId = sessionId;
        this.link = link;
        this.recorder = recorder;
        this.settings = settings;
    }

    public static ViewerSession join(RelayTransport transport, Database db, String sessionId, String password,
                                     String transportName, Settings settings) {
        var keys = Handshake.join(transport, sessionId, password);
        var status = Sessions.status(transport, sessionId);
        var recorder = Recorder.open(db, sessionId, "viewer", status.hostName(), status.shell(), transportName,
                settings == null || !Boolean.FALSE.equals(settings.recordOutput()));
        var link = new RelayLink(transport, sessionId, Role.VIEWER, keys, RelayTransport.LAST_ID);
        var s = new ViewerSession(transport, db, sessionId, link, recorder, settings);
        link.start(s::onFrame, e -> recorder.error("relay: " + e));
        recorder.control("joined session " + Ids.prettySessionId(sessionId)
                         + " on " + (status.hostName() == null ? "the host" : status.hostName()));
        return s;
    }

    public String sessionId() {
        return sessionId;
    }

    public Recorder recorder() {
        return recorder;
    }

    /** Raw shell bytes from the far end, and only ever those. */
    public void tap(Consumer<byte[]> listener) {
        taps.add(listener);
    }

    /** What the tool did, kept off the terminal's stream for the same reason it is on the host:
     *  a log written into the middle of a program's output belongs to neither. */
    public void onActivity(Consumer<String> listener) {
        activity.add(listener);
    }

    @Override
    public void note(String text) {
        for (var a : activity) {
            try {
                a.accept(text);
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Called whenever the host's STATE changes, or a refusal arrives, or the far end goes away. */
    public void onChange(Runnable r) {
        watchers.add(r);
    }

    public RemoteState remote() {
        return remote;
    }

    @Override
    public void send(FrameType type, byte[] payload) {
        link.send(type, payload);
    }

    @Override
    public Settings settings() {
        return settings;
    }

    /** True when the session turned out to be hosted on this very machine, so the loopback was
     *  taken instead of the relay - and a file transfer has nothing to cross. */
    @Override
    public boolean sameMachine() {
        return transport.local();
    }

    /** The host is the end that enforces its own restrictions; this end never refuses on its behalf. */
    @Override
    public boolean refuse(String what) {
        return false;
    }

    /** From the host's STATE frame, so a large transfer can be planned before anything is staged. */
    @Override
    public boolean peerShared() {
        return remote.sharedExchange();
    }

    /** The host publishes its STATE the moment it admits a viewer, so having heard it at all is the
     *  proof that this end is joined and not merely waiting to be let in. */
    @Override
    public boolean attached() {
        return remote.known();
    }

    public FileMover files() {
        return files;
    }

    /** Supplied by the window: the far end's screen as this end has it rendered. An agent asking
     *  what is on the screen wants this and not the whole transcript. */
    public void snapshot(java.util.function.Supplier<String> s) {
        snapshot = s;
    }

    public String screen() {
        var s = snapshot;
        return s == null ? null : s.get();
    }

    public boolean closed() {
        return closed;
    }

    void changed() {
        for (var w : watchers) {
            try {
                w.run();
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Types into the far shell, then gives the far end a short moment to say no.
     *  <p>
     *  The window is short on purpose - blocking an interactive keystroke for seconds to confirm
     *  it was accepted is worse than the problem. A refusal that arrives after the window is not
     *  lost: it stays unreported and the next command surfaces it, so "it did nothing and said
     *  nothing" never happens even when the far end is on the slow transport. */
    public String sendKeys(String text, Duration waitForRefusal) {
        var sentAt = System.currentTimeMillis();
        link.send(FrameType.INPUT, text);
        recorder.input("viewer", text);
        var deadline = System.nanoTime() + waitForRefusal.toNanos();
        while (System.nanoTime() < deadline) {
            if (lastRefusalAt.get() >= sentAt) break;
            HostSession.sleep(Duration.ofMillis(25));
        }
        return takeUnreportedRefusal();
    }

    /** Keystrokes, byte for byte, with no wait for a refusal. The window uses this rather than
     *  {@link #sendKeys}: a refusal already arrives as an ERROR frame and reaches the screen
     *  through the same tap as everything else, so there is nothing to block a keypress for. */
    public void sendRaw(byte[] bytes) {
        link.send(FrameType.INPUT, bytes);
        recorder.input("viewer", new String(bytes, StandardCharsets.UTF_8));
    }

    /** The most recent refusal nobody has been told about yet, or null. */
    public String takeUnreportedRefusal() {
        var at = lastRefusalAt.get();
        if (at <= reportedRefusalAt.get()) return null;
        reportedRefusalAt.set(at);
        return lastRefusal.get();
    }

    public String peekRefusal() {
        return lastRefusal.get();
    }

    public void resize(int columns, int rows) {
        var n = Wire.obj();
        n.put("columns", columns);
        n.put("rows", rows);
        link.send(FrameType.RESIZE, Wire.line(n).getBytes(StandardCharsets.UTF_8));
    }

    public String execDetached(String command, String cwd, Duration timeout, String stdin) {
        var jobId = ExecWire.newJobId();
        pending.put(jobId, new CompletableFuture<>());
        stdout.put(jobId, new StringBuilder());
        stderr.put(jobId, new StringBuilder());
        startedAt.put(jobId, System.currentTimeMillis());
        recorder.startJob(jobId, command, cwd, "agent");
        link.send(FrameType.EXEC_REQUEST, ExecWire.request(jobId, command, cwd,
                (timeout == null ? ExecRunner.DEFAULT_TIMEOUT : timeout).toMillis(), stdin));
        return jobId;
    }

    public boolean tracking(String jobId) {
        return pending.containsKey(jobId);
    }

    public ExecResult await(String jobId, Duration wait) {
        var f = pending.get(jobId);
        if (f == null) throw new NoSuchElementException("no job " + jobId + " is being tracked here");
        try {
            return f.get(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return new ExecResult(jobId, null, ExecResult.TIMEOUT, 0, 0, wait.toMillis(),
                    text(stdout, jobId), text(stderr, jobId));
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting for " + jobId);
        }
    }

    public ExecResult exec(String command, String cwd, Duration timeout, String stdin) {
        var limit = timeout == null ? ExecRunner.DEFAULT_TIMEOUT : timeout;
        return await(execDetached(command, cwd, limit, stdin), limit.plusSeconds(15));
    }

    public void cancel(String jobId) {
        link.send(FrameType.EXEC_CANCEL, ExecWire.cancel(jobId));
    }

    void onFrame(Frame f) {
        switch (f.type()) {
            case OUTPUT -> {
                recorder.output("host", f.asText());
                fanOut(f.payload());
            }
            // Not recorded: these are the far end's log of what it did, and this end already
            // records the same events from its own side. They belong in the activity pane.
            case HELLO, CONTROL -> note(f.asText());
            case EXEC_STDOUT -> chunk(f, stdout, Streams.EXEC_STDOUT);
            case EXEC_STDERR -> chunk(f, stderr, Streams.EXEC_STDERR);
            case EXEC_EXIT -> finish(f);
            case FILE_OFFER, FILE_CHUNK, FILE_DONE, FILE_REQUEST -> files.onFrame(f);
            case STATE -> {
                remote = RemoteState.of(ExecWire.read(f.payload()));
                changed();
            }
            case ERROR -> {
                recorder.error(f.asText());
                lastRefusal.set(f.asText());
                lastRefusalAt.set(System.currentTimeMillis());
                note(f.asText());
                changed();
            }
            case PONG -> { }
            case BYE -> {
                recorder.control("the host ended the session");
                note("the host ended the session");
                changed();
            }
            default -> recorder.control("ignored frame " + f.type());
        }
    }

    void chunk(Frame f, Map<String, StringBuilder> sink, String stream) {
        var n = ExecWire.read(f.payload());
        var jobId = Wire.str(n, "jobId", "");
        var text = Wire.str(n, "text", "");
        sink.computeIfAbsent(jobId, k -> new StringBuilder()).append(text);
        recorder.jobOutput(jobId, stream, text);
    }

    void finish(Frame f) {
        var n = ExecWire.read(f.payload());
        var jobId = Wire.str(n, "jobId", "");
        var exit = Wire.boxedInt(n, "exitCode");
        var state = Wire.str(n, "state", ExecResult.FAILED);
        var started = startedAt.getOrDefault(jobId, System.currentTimeMillis());
        var r = new ExecResult(jobId, exit, state,
                Wire.l(n, "stdoutBytes", 0), Wire.l(n, "stderrBytes", 0),
                Wire.l(n, "millis", System.currentTimeMillis() - started),
                text(stdout, jobId), text(stderr, jobId));
        recorder.finishJob(jobId, exit, r.stdoutBytes(), r.stderrBytes(), state);
        var future = pending.get(jobId);
        if (future != null) future.complete(r);
    }

    void fanOut(byte[] bytes) {
        for (var t : taps) {
            try {
                t.accept(bytes);
            } catch (RuntimeException ignored) {
            }
        }
    }

    static String text(Map<String, StringBuilder> m, String jobId) {
        var b = m.get(jobId);
        return b == null ? "" : b.toString();
    }

    public SessionStatus status() {
        return Sessions.status(transport, sessionId);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            files.close();
        } catch (RuntimeException ignored) {
        }
        try {
            link.send(FrameType.BYE, new byte[0]);
        } catch (RuntimeException ignored) {
        }
        link.close();
        recorder.end("viewer left");
        changed();
    }
}
