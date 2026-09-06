package cfucli.app;

import module java.base;
import com.techsenger.jeditermfx.core.TtyConnector;
import com.techsenger.jeditermfx.ui.DefaultHyperlinkFilter;
import javafx.geometry.Dimension2D;
import javafx.scene.layout.Pane;

/** The terminal widget, wired to a connector and nothing else. Both roles use this - the only
 *  difference between the host console and the viewer mirror is which connector goes in. */
public final class TerminalView implements AutoCloseable {

    static final String ESC = String.valueOf((char) 27), CLEAR_AND_HOME = ESC + "[2J" + ESC + "[H";

    public static final double FONT_STEP = 1;

    final ThemedTerminalWidget widget;
    final TerminalTheme theme;
    final TtyConnector connector;

    public TerminalView(TtyConnector connector, int columns, int rows, double fontSize) {
        this.connector = connector;
        this.theme = new TerminalTheme(fontSize);
        this.widget = new ThemedTerminalWidget(columns, rows, theme);
        widget.setTtyConnector(connector);
        widget.addHyperlinkFilter(new DefaultHyperlinkFilter());
        widget.start();
    }

    public Pane pane() {
        return widget.getPane();
    }

    public Dimension2D preferredSize() {
        return widget.getPreferredSize();
    }

    public int columns() {
        return widget.getTerminalTextBuffer().getWidth();
    }

    public int rows() {
        return widget.getTerminalTextBuffer().getHeight();
    }

    public double fontSize() {
        return theme.size();
    }

    /** Returns the size actually applied, which is not always the one asked for - the range is
     *  bounded, and returning it lets the caller report the truth rather than the wish. */
    public double fontSize(double wanted) {
        var applied = TerminalTheme.clamp(wanted);
        if (applied == theme.size()) return applied;
        theme.size(applied);
        widget.themedPanel().refreshFont();
        return applied;
    }

    /** The screen as text, and nothing else - no escape sequences.
     *  <p>
     *  Two very different things want it: a viewer joining mid-session, which writes it into an
     *  emulator and needs it framed with a clear-and-home, and an agent asking what is on the
     *  screen, which wants to read it. Framing it here would have served the first and handed the
     *  second an answer beginning with control codes, so the framing belongs to the sender.
     *  <p>
     *  Normalised to CRLF because the emulator case is written in raw, where a bare LF drops a
     *  line without a carriage return and staircases the whole screen. */
    public String screen() {
        var text = widget.getTerminalTextBuffer().getScreenLines();
        if (text == null || text.isBlank()) return "";
        return text.replace("\r\n", "\n").replace("\n", "\r\n").stripTrailing() + "\r\n";
    }

    public void clear() {
        widget.getTerminalTextBuffer().clearScreenAndHistoryBuffers();
        widget.getTerminalPanel().repaint();
    }

    public void focus() {
        var n = widget.getPreferredFocusableNode();
        if (n != null) n.requestFocus();
    }

    @Override
    public void close() {
        try {
            widget.close();
        } catch (RuntimeException ignored) {
        }
        connector.close();
    }
}
