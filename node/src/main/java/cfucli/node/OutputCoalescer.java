package cfucli.node;

import module java.base;
import java.util.function.Consumer;

/** Terminal output arrives in whatever pieces the shell felt like writing - often a byte at a
 *  time while someone types. Publishing one relay message per piece would spend the free monthly
 *  command allowance in a couple of busy days, so output is gathered for a few tens of
 *  milliseconds and sent as one frame. Latency nobody can feel; an order of magnitude fewer
 *  commands. */
public final class OutputCoalescer implements AutoCloseable {

    public static final int DEFAULT_MAX_BYTES = 16 * 1024;
    public static final Duration DEFAULT_WINDOW = Duration.ofMillis(60);

    final int maxBytes;
    final Consumer<byte[]> sink;
    final ScheduledExecutorService timer;
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    /** Not a constant, because what it is protecting is a property of where the bytes are going.
     *  Sixty milliseconds is the price of the relay's metered commands; the loopback pays nothing
     *  and gets zero, which passes each chunk straight through as the shell produces it. */
    volatile Duration window;

    ScheduledFuture<?> pending;

    public OutputCoalescer(int maxBytes, Duration window, Consumer<byte[]> sink) {
        this.maxBytes = maxBytes <= 0 ? DEFAULT_MAX_BYTES : maxBytes;
        this.window = window == null ? DEFAULT_WINDOW : window;
        this.sink = sink;
        this.timer = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "coalescer");
            t.setDaemon(true);
            return t;
        });
    }

    /** Changes where the trade sits, for a session that has just attached to a different kind of
     *  far end. Anything already buffered goes out under the old window first. */
    public void window(Duration d) {
        if (d == null || d.equals(window)) return;
        flush();
        window = d;
    }

    public Duration window() {
        return window;
    }

    public void add(byte[] chunk) {
        if (chunk == null || chunk.length == 0) return;
        var flushNow = (byte[]) null;
        synchronized (buffer) {
            buffer.write(chunk, 0, chunk.length);
            if (buffer.size() >= maxBytes || window.isZero() || window.isNegative()) {
                flushNow = take();
            } else if (pending == null) {
                pending = timer.schedule(this::flush, window.toMillis(), TimeUnit.MILLISECONDS);
            }
        }
        if (flushNow != null) sink.accept(flushNow);
    }

    public void flush() {
        var out = (byte[]) null;
        synchronized (buffer) {
            out = take();
        }
        if (out != null) sink.accept(out);
    }

    byte[] take() {
        if (pending != null) {
            pending.cancel(false);
            pending = null;
        }
        if (buffer.size() == 0) return null;
        var out = buffer.toByteArray();
        buffer.reset();
        return out;
    }

    @Override
    public void close() {
        flush();
        timer.shutdownNow();
    }
}
