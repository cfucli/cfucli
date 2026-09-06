package cfucli.app;

import module java.base;

/** Bytes arriving from anywhere, read as a stream.
 *  <p>
 *  It exists because the terminal widget reads {@code char}, while both a pty and a relay deliver
 *  {@code byte} in chunks that split wherever they happen to split. Wrapping this in an
 *  {@link java.io.InputStreamReader} hands the multi-byte boundary problem to the JDK's decoder,
 *  which is the one place it is already right - a Devanagari character cut in half across two
 *  reads survives, where decoding each chunk on its own turns it into two replacement marks.
 *  <p>
 *  Unbounded on purpose: the writer is the pty pump or the relay reader, and neither may be made
 *  to wait because a window is slow to paint. */
public final class ByteQueueStream extends InputStream {

    static final byte[] EOF = new byte[0];

    final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();

    byte[] current = EOF;
    int pos;
    volatile boolean ended;

    public void push(byte[] chunk) {
        if (!ended && chunk != null && chunk.length > 0) queue.add(chunk);
    }

    public void end() {
        if (ended) return;
        ended = true;
        queue.add(EOF);
    }

    boolean fill() throws IOException {
        while (pos >= current.length) {
            if (ended && queue.isEmpty()) return false;
            try {
                var next = queue.take();
                if (next.length == 0) return false;
                current = next;
                pos = 0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("interrupted while reading terminal output");
            }
        }
        return true;
    }

    @Override
    public int read() throws IOException {
        return fill() ? current[pos++] & 0xFF : -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) return 0;
        if (!fill()) return -1;
        var n = Math.min(len, current.length - pos);
        System.arraycopy(current, pos, b, off, n);
        pos += n;
        return n;
    }

    @Override
    public int available() {
        var n = current.length - pos;
        for (var chunk : queue) n += chunk.length;
        return n;
    }

    @Override
    public void close() {
        end();
    }
}
