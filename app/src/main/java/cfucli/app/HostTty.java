package cfucli.app;

import module java.base;
import com.techsenger.jeditermfx.core.TtyConnector;
import com.techsenger.jeditermfx.core.util.TermSize;
import cfucli.node.HostSession;

/** The host window's view of the shared shell.
 *  <p>
 *  Deliberately not a second process: this taps the one pty the session already owns, so the local
 *  window and the remote end are two subscribers to one shell rather than two shells. Typing here
 *  goes through {@code typeLocally}, which records it and lets the shell's own echo carry it out
 *  to the viewer - so nothing is echoed twice and the audit log sees both sides' keystrokes. */
public final class HostTty implements TtyConnector {

    final HostSession session;
    final ByteQueueStream bytes = new ByteQueueStream();
    final InputStreamReader reader;

    public HostTty(HostSession session) {
        this.session = session;
        this.reader = new InputStreamReader(bytes, StandardCharsets.UTF_8);
        session.tap(bytes::push);
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        return reader.read(buf, offset, length);
    }

    @Override
    public void write(byte[] b) {
        session.typeLocally(b);
    }

    @Override
    public void write(String s) {
        write(s.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isConnected() {
        return session.pty().alive();
    }

    @Override
    public void resize(TermSize size) {
        session.resizeLocally(size.getColumns(), size.getRows());
    }

    @Override
    public int waitFor() throws InterruptedException {
        return session.pty().waitFor();
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public String getName() {
        return session.pty().command();
    }

    /** Ends this window's read loop and nothing else. The shell belongs to the session, and the
     *  session is ended by ending the session - not by a widget being disposed. */
    @Override
    public void close() {
        bytes.end();
    }
}
