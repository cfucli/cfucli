package cfucli.app;

import module java.base;
import javafx.scene.Node;
import javafx.scene.control.TextArea;

/** What the tool did, kept out of the terminal.
 *  <p>
 *  The two belong on different channels and the reason is not tidiness. A terminal is a byte
 *  stream a program is drawing on: a line of ours written into it lands in the middle of whatever
 *  was being drawn, survives into any transcript taken from that screen, and cannot be told apart
 *  from the program's own output by anything downstream. So the shell gets the terminal, and
 *  everything about the shell - a command an agent ran, a viewer arriving, a refusal - comes here.
 *  <p>
 *  Hidden by default and toggled with one key, because most of the time there is nothing to read;
 *  the legend says how many lines arrived while it was shut, so nothing happens unannounced. */
public final class LogPane {

    public static final int
            MAX_LINES = 500,
            LOUD_AFTER = 5;

    static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    final TextArea area = new TextArea();
    final Deque<String> lines = new ArrayDeque<>();

    int unseen;
    boolean showing;

    public LogPane() {
        area.setEditable(false);
        area.setWrapText(false);
        area.setFocusTraversable(false);
        area.getStyleClass().add("log");
        area.setPrefRowCount(8);
    }

    public Node node() {
        return area;
    }

    /** On the FX thread. */
    public void add(String text) {
        if (text == null || text.isBlank()) return;
        lines.addLast(CLOCK.format(Instant.now()) + "  " + text.strip());
        while (lines.size() > MAX_LINES) lines.removeFirst();
        area.setText(String.join(System.lineSeparator(), lines));
        area.positionCaret(area.getLength());
        area.setScrollTop(Double.MAX_VALUE);
        if (!showing) unseen++;
    }

    public boolean showing() {
        return showing;
    }

    public void showing(boolean b) {
        showing = b;
        if (b) unseen = 0;
    }

    public int unseen() {
        return unseen;
    }

    /** What the legend says about it, which is nothing at all when nothing has happened.
     *  <p>
     *  It names the key, because a bare count tells a person something is being withheld without
     *  telling them how to look. F12 is listed elsewhere in the same legend, but a key in a list of
     *  six and a count at the other end of the bar are not joined up at the moment it matters. */
    public String hint() {
        if (showing || unseen == 0) return "";
        return (unseen == 1 ? "1 new line" : unseen + " new lines") + " in the activity log - F12 to read";
    }

    /** Past a handful of unread lines a grey note is no longer proportionate to what is being
     *  missed, so the style changes and not only the number. An agent driving this session writes
     *  a line per command, so this is what a human watching the terminal alone sees escalate. */
    public boolean loud() {
        return !showing && unseen >= LOUD_AFTER;
    }
}
