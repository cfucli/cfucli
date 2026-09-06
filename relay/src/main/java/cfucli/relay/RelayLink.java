package cfucli.relay;

import module java.base;
import java.util.function.Consumer;

/** One end of a live session: an outbound stream it appends to, an inbound stream it follows.
 *  Sequence numbers are per direction and start at zero, which is safe because each direction has
 *  its own key and so its own nonce space. */
public final class RelayLink implements AutoCloseable {

    public static final long MAX_STREAM_ENTRIES = 20_000;
    public static final int READ_BATCH = 256;
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
        var f = Frame.of(type, outSeq.getAndIncrement(), payload, role.outbound());
        transport.append(outStream, FrameCodec.encode(f, role.outbound(), keys), MAX_STREAM_ENTRIES);
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
        while (running) {
            try {
                for (var r : transport.read(inStream, cursor, READ_BATCH, BLOCK)) {
                    cursor = r.id();
                    deliver(r, onFrame, onError);
                }
            } catch (RuntimeException e) {
                if (!running || Thread.currentThread().isInterrupted()) return;
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
