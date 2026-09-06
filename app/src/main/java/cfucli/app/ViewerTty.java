package cfucli.app;

import module java.base;
import com.techsenger.jeditermfx.core.TtyConnector;
import com.techsenger.jeditermfx.core.util.TermSize;
import cfucli.node.ViewerSession;

/** The mirror. Everything the far shell prints arrives through the session's tap; everything typed
 *  here goes out as an INPUT frame and comes back as the far shell's own echo.
 *  <p>
 *  {@link #resize} does nothing on purpose. The host owns the geometry, because a viewer that
 *  reflowed the shared shell to its own window would garble the screen of the person whose machine
 *  it is. The window matches the size the host reports instead. */
public final class ViewerTty implements TtyConnector {

    final ViewerSession session;
    final ByteQueueStream bytes = new ByteQueueStream();
    final InputStreamReader reader;

    public ViewerTty(ViewerSession session) {
        this.session = session;
        this.reader = new InputStreamReader(bytes, StandardCharsets.UTF_8);
        session.tap(bytes::push);
        session.onChange(() -> {
            if (session.closed()) bytes.end();
        });
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        return reader.read(buf, offset, length);
    }

    @Override
    public void write(byte[] b) {
        session.sendRaw(b);
    }

    @Override
    public void write(String s) {
        write(s.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isConnected() {
        return !session.closed();
    }

    @Override
    public void resize(TermSize size) {
    }

    @Override
    public int waitFor() throws InterruptedException {
        while (!session.closed()) Thread.sleep(200);
        return 0;
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public String getName() {
        var r = session.remote();
        return (r.shell() == null ? "remote shell" : r.shell())
               + (r.hostName() == null ? "" : " on " + r.hostName());
    }

    @Override
    public void close() {
        bytes.end();
    }
}
