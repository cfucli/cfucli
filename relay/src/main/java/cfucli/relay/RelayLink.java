package cfucli.relay;

import module java.base;
import java.util.function.Consumer;

/** One end of a live session: an outbound stream it appends to, an inbound stream it follows.
 *  Sequence numbers are per direction and start at zero, which is safe because each direction has
 *  its own key and so its own nonce space. */
public final class RelayLink implements AutoCloseable {

    public static final long MAX_STREAM_ENTRIES = 20_000;

    /** Upstash counts the REPLY against its 10MB request limit, not just the command. A file chunk
     *  is about 117K on the relay once it has been base64'd, encrypted and base64'd again, so 256
     *  of them is a 30MB reply that is refused every time - and the reader used to retry that same
     *  read for ever, which looked to everyone like the far machine had died. Measured 2026-10-03:
     *  80 entries came back at 9.3MB, 100 were refused. 32 stays near 4MB at worst. */
    public static final int READ_BATCH = 32;

    /** Trim what has been consumed once this much has been read since the last trim. Each trim is a
     *  metered command, so it is not done per read; this keeps a stream on the relay to roughly
     *  what is in flight instead of a whole file sitting in a database capped at 256MB. */
    static final long TRIM_AFTER_BYTES = 512 * 1024;

    static final Duration BLOCK = Duration.ofSeconds(20), ERROR_BACKOFF = Duration.ofSeconds(2);

    final RelayTransport transport;
    final String sessionId, outStream, inStream;
    final Role role;
    final SessionKeys keys;
    final AtomicLong outSeq = new AtomicLong();
    final AtomicLong undecryptable = new AtomicLong();

    volatile String cursor;
    volatile boolean running;
    Thread reader;

    public RelayLink(RelayTransport transport, String sessionId, Role role, SessionKeys keys, String startCursor) {
        this.transport = transport;
        this.sessionId = sessionId;
        this.role = role;
        this.keys = keys;
        this.outStream = Channels.stream(sessionId, role.outbound());
        this.inStream = Channels.stream(sessionId, role.inbound());
        this.cursor = startCursor == null ? RelayTransport.LAST_ID : startCursor;
    }

    public String sessionId() {
        return sessionId;
    }

    public Role role() {
        return role;
    }

    public String cursor() {
        return cursor;
    }

    public long undecryptableFrames() {
        return undecryptable.get();
    }

    public Frame send(FrameType type, byte[] payload) {
        var seq = outSeq.getAndIncrement();
        var f = Frame.of(type, seq, payload, role.outbound());
        transport.append(outStream, FrameCodec.encode(f, role.outbound(), keys), MAX_STREAM_ENTRIES);
        // EXPIRE on a key that does not exist yet does nothing, and the stream only exists once
        // something has been appended - so the TTL set in start() never took on a stream nobody had
        // written to, and every viewer-to-host stream lived for ever. Set it once the stream is real.
        if (seq == 0) transport.touch(outStream, Meta.STREAM_TTL);
        return f;
    }

    public Frame send(FrameType type, String text) {
        return send(type, text.getBytes(StandardCharsets.UTF_8));
    }

    public void start(Consumer<Frame> onFrame, Consumer<Throwable> onError) {
        if (running) throw new IllegalStateException("link already started");
        running = true;
        if (RelayTransport.LAST_ID.equals(cursor)) cursor = transport.lastId(inStream);
        transport.touch(outStream, Meta.STREAM_TTL);
        reader = Thread.ofPlatform().name("relay-" + role.wire() + "-" + sessionId).daemon()
                .start(() -> pump(onFrame, onError));
    }

    void pump(Consumer<Frame> onFrame, Consumer<Throwable> onError) {
        var batch = READ_BATCH;
        var sinceTrim = 0L;
        while (running) {
            try {
                for (var r : transport.read(inStream, cursor, batch, BLOCK)) {
                    cursor = r.id();
                    sinceTrim += r.payload().length;
                    deliver(r, onFrame, onError);
                }
                batch = READ_BATCH;
                if (sinceTrim >= TRIM_AFTER_BYTES) {
                    sinceTrim = 0;
                    trimConsumed();
                }
            } catch (RuntimeException e) {
                if (!running || Thread.currentThread().isInterrupted()) return;
                // A reply over the relay's size limit is refused whole, so asking again for the
                // same amount can never succeed. Ask for less, straight away.
                if (tooLarge(e) && batch > 1) {
                    batch = Math.max(1, batch / 2);
                    continue;
                }
                onError.accept(e);
                // Backing off must not itself throw on the way down. close() sets running false
                // and then interrupts this thread, so an interrupt arriving inside the backoff is
                // the stop signal rather than a second failure - and an IllegalStateException
                // escaping here kills the thread loudly and leaves a stack trace in the node log
                // that reads like a fault. Cycle 04's expiry thread had the same shape.
                if (!backOff(ERROR_BACKOFF)) return;
            }
        }
    }

    static boolean tooLarge(Throwable e) {
        for (var t = e; t != null; t = t.getCause()) {
            var m = t.getMessage();
            if (m != null && m.toLowerCase(Locale.ROOT).contains("max request size")) return true;
        }
        return false;
    }

    /** Best effort: a trim that fails costs only storage, never the session. */
    void trimConsumed() {
        try {
            transport.trim(inStream, cursor);
        } catch (RuntimeException ignored) {
        }
    }

    /** Sleeps, and says whether it got to the end. False means we were interrupted, which here
     *  only ever means the link is closing. */
    static boolean backOff(Duration d) {
        try {
            Thread.sleep(d.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    void deliver(StreamRecord r, Consumer<Frame> onFrame, Consumer<Throwable> onError) {
        try {
            onFrame.accept(FrameCodec.decode(r.payload(), role.inbound(), keys));
        } catch (SecurityException | IllegalArgumentException e) {
            if (undecryptable.getAndIncrement() == 0) onError.accept(e);
        }
    }

    public void stop() {
        running = false;
        if (reader != null) reader.interrupt();
    }

    @Override
    public void close() {
        stop();
    }
}
