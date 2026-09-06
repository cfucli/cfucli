package cfucli.node;

import module java.base;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cfucli.relay.Frame;
import cfucli.relay.FrameType;
import static cfucli.node.FileWire.*;

/** Moving files between the two ends, by whichever of the three routes actually fits.
 *  <p>
 *  One of these lives on each end and they are the same class, because a put and a get are the same
 *  machinery with the sender and receiver swapped. The end that ASKED holds a future and blocks on
 *  it; the end that RECEIVED sends the FILE_DONE that resolves it. That is the whole protocol. */
public final class FileMover implements AutoCloseable {

    public static final Duration DEFAULT_WAIT = Duration.ofMinutes(5), ATTACH_WAIT = Duration.ofSeconds(3);

    final FileEnd end;
    final FileTake take = new FileTake(this);
    final Map<String, CompletableFuture<ObjectNode>> pending = new ConcurrentHashMap<>();
    final Map<String, Path> wanted = new ConcurrentHashMap<>();
    final ExecutorService workers = Executors.newCachedThreadPool(r -> {
        var t = new Thread(r, "file-xfer");
        t.setDaemon(true);
        return t;
    });

    public FileMover(FileEnd end) {
        this.end = end;
    }

    /** Send a file to the far end. Blocks until the far end says it has it, or until the wait runs
     *  out, and reports which route was taken either way. */
    public ObjectNode put(String localPath, String dest, String via, boolean force, Duration wait) {
        requireAttached();
        var source = FilePaths.require(localPath);
        var size = FilePaths.size(source);
        var plan = FileRoute.choose(via, source.getFileName().toString(), size, threshold(),
                end.sameMachine(), FileStage.available(end.settings()), end.peerShared() || end.sameMachine());
        var xferId = newXferId();
        var f = track(xferId);
        end.note("put " + xferId + ": " + source.getFileName() + " " + FileRoute.human(size) + " - " + plan.why());
        submit(xferId, () -> FileSend.run(end, xferId, source, dest, plan, force, millis(wait)));
        return awaited(xferId, f, wait, plan.route(), source.getFileName().toString());
    }

    /** Fetch a file from the far end. The local destination is resolved before anything is asked
     *  for, so "it already exists" costs nothing and arrives before the transfer rather than after. */
    public ObjectNode get(String remotePath, String localDest, String via, boolean force, Duration wait) {
        requireAttached();
        var name = FilePaths.nameOf(remotePath);
        var target = FilePaths.resolveDest(localDest, name, force);
        var xferId = newXferId();
        wanted.put(xferId, target);
        var f = track(xferId);
        var n = Wire.obj();
        n.put(XFER, xferId).put(PATH, remotePath).put(VIA, via == null || via.isBlank() ? FileRoute.AUTO : via)
                .put(SHARED, FileStage.available(end.settings())).put(SAME_MACHINE, end.sameMachine())
                .put(WAIT_MS, millis(wait)).put(FORCE, force);
        end.note("get " + xferId + ": " + remotePath + " -> " + target);
        end.send(FrameType.FILE_REQUEST, bytes(n));
        return awaited(xferId, f, wait, null, name);
    }

    public void onFrame(Frame f) {
        var payload = read(f.payload());
        var xferId = Wire.str(payload, XFER, "");
        try {
            switch (f.type()) {
                case FILE_OFFER -> take.offer(payload);
                case FILE_CHUNK -> take.chunk(payload);
                case FILE_REQUEST -> take.request(payload);
                case FILE_DONE -> done(payload);
                default -> { }
            }
        } catch (RuntimeException e) {
            abort(xferId, e);
        }
    }

    /** Whoever received the file says so, and that is what unblocks whoever asked for it. */
    void complete(String xferId, Path target, long bytes, long millis, String route) {
        var n = result(xferId, route);
        n.put(OK, true).put(PATH, target.toString()).put(BYTES, bytes).put(MILLIS, millis);
        end.note(xferId + " done: " + target + " " + FileRoute.human(bytes) + " in " + millis + "ms");
        finish(xferId, n);
        end.send(FrameType.FILE_DONE, bytes(n));
    }

    /** Something went wrong on this side. The far end is told so it can drop a half-written part
     *  file, and anyone blocked here stops blocking rather than waiting out the whole timeout. */
    void abort(String xferId, Throwable cause) {
        take.drop(xferId);
        wanted.remove(xferId);
        var why = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        var n = result(xferId, null);
        n.put(OK, false).put(ERROR, why);
        end.note(xferId + " failed: " + why);
        finish(xferId, n);
        end.send(FrameType.FILE_DONE, bytes(n));
    }

    void done(com.fasterxml.jackson.databind.JsonNode payload) {
        var xferId = Wire.str(payload, XFER, "");
        if (!Wire.bool(payload, OK, false)) take.drop(xferId);
        var f = pending.remove(xferId);
        if (f != null) {
            f.complete(payload.deepCopy());
            return;
        }
        end.note(xferId + (Wire.bool(payload, OK, false)
                ? " confirmed by the far end" : " refused by the far end: " + Wire.str(payload, ERROR, "no reason given")));
    }

    /** Fail in three seconds with the reason, rather than in five minutes with nothing.
     *  <p>
     *  Measured: a put issued against a windowed host that had not yet approved the connection sat
     *  for its full timeout and printed nothing, because the frames were going into a session the
     *  host had not attached to. The short wait is there because a viewer that has only just joined
     *  is legitimately a moment away from hearing the host's first STATE. */
    void requireAttached() {
        var deadline = System.nanoTime() + ATTACH_WAIT.toNanos();
        while (!end.attached()) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("the far end has not joined this session yet - if it is running the"
                        + " window, someone there has to approve the connection first. 'status' says which it is.");
            }
            HostSession.sleep(Duration.ofMillis(100));
        }
    }

    void submit(String xferId, Runnable work) {
        workers.submit(() -> {
            try {
                work.run();
            } catch (RuntimeException e) {
                abort(xferId, e);
            }
        });
    }

    long threshold() {
        var t = end.settings() == null ? null : end.settings().largeFileThresholdBytes();
        return t == null ? FileRoute.DEFAULT_THRESHOLD : t;
    }

    CompletableFuture<ObjectNode> track(String xferId) {
        var f = new CompletableFuture<ObjectNode>();
        pending.put(xferId, f);
        return f;
    }

    void finish(String xferId, ObjectNode n) {
        var f = pending.remove(xferId);
        if (f != null) f.complete(n.deepCopy());
    }

    ObjectNode awaited(String xferId, CompletableFuture<ObjectNode> f, Duration wait, String route, String name) {
        try {
            return f.get(millis(wait), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pending.remove(xferId);
            take.drop(xferId);
            wanted.remove(xferId);
            var n = result(xferId, route);
            return n.put(OK, false).put(ERROR, name + " did not finish within "
                    + millis(wait) / 1000 + "s - it may still be in flight; pass a longer --timeout");
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting for " + xferId);
        }
    }

    ObjectNode result(String xferId, String route) {
        var n = Wire.obj();
        n.put(XFER, xferId);
        if (route != null) n.put(ROUTE, route);
        return n;
    }

    static long millis(Duration d) {
        return (d == null ? DEFAULT_WAIT : d).toMillis();
    }

    @Override
    public void close() {
        workers.shutdownNow();
        take.closeAll();
        pending.values().forEach(f -> f.cancel(true));
        pending.clear();
        wanted.clear();
    }
}
