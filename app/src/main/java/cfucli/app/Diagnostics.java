package cfucli.app;

import module java.base;
import com.techsenger.jeditermfx.core.StyledTextConsumerAdapter;
import com.techsenger.jeditermfx.core.TerminalColor;
import com.techsenger.jeditermfx.core.TextStyle;
import com.techsenger.jeditermfx.core.model.CharBuffer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;

/** Prints what the terminal cells actually carry, on stderr, when CFUCLI_DUMP_CELLS is set.
 *  <p>
 *  Kept rather than deleted: when a terminal renders in the wrong colours, the only way to tell a
 *  theme that is not being applied from a shell asking for other colours is to look at the styles
 *  in the buffer, and a probe in a test source cannot see the window a person is complaining
 *  about. */
public final class Diagnostics {

    public static final String FLAG = "CFUCLI_DUMP_CELLS";

    public static void maybeDumpCells(TerminalView view, String label, Duration after) {
        if (System.getenv(FLAG) == null) return;
        var once = new Timeline(new KeyFrame(javafx.util.Duration.millis(after.toMillis()),
                e -> dump(view, label)));
        once.play();
    }

    public static void dump(TerminalView view, String label) {
        var panel = view.widget.getTerminalPanel();
        System.err.println("[cells] " + label + "  " + view.columns() + "x" + view.rows()
                           + "  font=" + view.fontSize()
                           + "  windowFg=" + panel.getWindowForeground()
                           + "  windowBg=" + panel.getWindowBackground()
                           + "  canvasFill=" + panel.getBackground()
                           + "  selection=" + panel.selectionProperty().get());
        view.widget.getTerminalTextBuffer().processHistoryAndScreenLines(0, 10, new StyledTextConsumerAdapter() {
            @Override
            public void consume(int x, int y, TextStyle style, CharBuffer characters, int startRow) {
                var text = characters.toString();
                if (text.isBlank()) return;
                System.err.println("[cells]   row " + y + " x" + x
                                   + "  fg=" + colour(style.getForeground())
                                   + " bg=" + colour(style.getBackground())
                                   + " opts=" + options(style)
                                   + "  class=" + style.getClass().getSimpleName()
                                   + "  [" + (text.length() > 46 ? text.substring(0, 46) : text) + "]");
            }
        });
    }

    static String options(TextStyle style) {
        var on = new ArrayList<String>();
        for (var o : TextStyle.Option.values()) if (style.hasOption(o)) on.add(o.name());
        return on.isEmpty() ? "-" : String.join("+", on);
    }

    static String colour(TerminalColor c) {
        if (c == null) return "null";
        return c.isIndexed() ? "index" + c.getColorIndex() : String.valueOf(c.toColor());
    }

    private Diagnostics() {}
}
