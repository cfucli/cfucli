package cfucli.app;

import module java.base;
import com.techsenger.jeditermfx.core.TtyConnector;
import com.techsenger.jeditermfx.core.util.TermSize;
import cfucli.node.PtyHost;

/** A connector over a bare PtyHost, for the probes. The app itself never uses this - it always
 *  goes through a session, so that everything typed is recorded and mirrored. */
public final class PtyTty implements TtyConnector {

    final PtyHost pty;
    final ByteQueueStream bytes = new ByteQueueStream();
    final InputStreamReader reader;

    public PtyTty(PtyHost pty) {
        this.pty = pty;
        this.reader = new InputStreamReader(bytes, StandardCharsets.UTF_8);
        pty.onOutput(bytes::push);
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        return reader.read(buf, offset, length);
    }

    @Override
    public void write(byte[] b) {
        pty.write(b);
    }

    @Override
    public void write(String s) {
        write(s.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isConnected() {
        return pty.alive();
    }

    @Override
    public void resize(TermSize size) {
        pty.resize(size.getColumns(), size.getRows());
    }

    @Override
    public int waitFor() throws InterruptedException {
        return pty.waitFor();
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public String getName() {
        return pty.command();
    }

    @Override
    public void close() {
        bytes.end();
    }
}
