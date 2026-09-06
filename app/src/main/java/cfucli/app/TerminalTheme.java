package cfucli.app;

import com.techsenger.jeditermfx.core.TerminalColor;
import com.techsenger.jeditermfx.core.TextStyle;
import com.techsenger.jeditermfx.ui.settings.DefaultSettingsProvider;
import javafx.scene.text.Font;

/** The shared shell's own look. Not black-on-white and not pure black either - the same near-black
 *  the rest of the window uses, so the terminal reads as part of the app rather than a hole in it.
 *  <p>
 *  The scrollback is deliberately large. This is the surface a person scrolls when something went
 *  wrong ten minutes ago, and the queryable record in the store is no substitute for being able to
 *  simply look up. */
public final class TerminalTheme extends DefaultSettingsProvider {

    public static final double MIN_SIZE = 8, MAX_SIZE = 34, DEFAULT_SIZE = 13.5;

    static final TerminalColor
            BACKGROUND = new TerminalColor(15, 19, 36),
            FOREGROUND = new TerminalColor(232, 234, 242);

    static final String[] CANDIDATES = {"Cascadia Mono", "Consolas", "Courier New", "Monospaced"};

    static final String FAMILY = pickFamily();

    volatile double size;

    public TerminalTheme(double size) {
        this.size = clamp(size);
    }

    public static double clamp(double wanted) {
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, wanted <= 0 ? DEFAULT_SIZE : wanted));
    }

    static String pickFamily() {
        for (var name : CANDIDATES) {
            var f = Font.font(name, 12);
            if (f != null && name.equalsIgnoreCase(f.getFamily())) return name;
        }
        return "Monospaced";
    }

    public double size() {
        return size;
    }

    public void size(double wanted) {
        size = clamp(wanted);
    }

    @Override
    public Font getTerminalFont() {
        return Font.font(FAMILY, size);
    }

    @Override
    public float getTerminalFontSize() {
        return (float) size;
    }

    /** The one that actually matters, and the reason a dark terminal took a while to get right.
     *  <p>
     *  In jeditermfx 1.1.0 the direction of derivation is the opposite of what the project's later
     *  source suggests: {@code getDefaultForeground()} and {@code getDefaultBackground()} are
     *  {@code default} methods that read THIS style, and the widget builds its StyleState from
     *  this one too. Overriding only the two colour methods therefore recolours the canvas fill
     *  and nothing else - every cell still resets to the hardcoded black-on-white, which paints as
     *  a white block behind each run of text on an otherwise dark screen. */
    @Override
    public TextStyle getDefaultStyle() {
        return new TextStyle(FOREGROUND, BACKGROUND);
    }

    @Override
    public TerminalColor getDefaultBackground() {
        return BACKGROUND;
    }

    @Override
    public TerminalColor getDefaultForeground() {
        return FOREGROUND;
    }

    /** An explicit selection colour rather than the library's inverse-video default: inverse of a
     *  plain cell on this palette is dark-on-light, which looks like a rendering fault rather than
     *  a selection. */
    @Override
    public boolean useInverseSelectionColor() {
        return false;
    }

    @Override
    public TextStyle getSelectionColor() {
        return new TextStyle(new TerminalColor(255, 255, 255), new TerminalColor(31, 78, 58));
    }

    @Override
    public TextStyle getFoundPatternColor() {
        return new TextStyle(new TerminalColor(20, 24, 43), new TerminalColor(242, 169, 59));
    }

    @Override
    public int getBufferMaxLinesCount() {
        return 20_000;
    }

    @Override
    public boolean audibleBell() {
        return false;
    }

    @Override
    public boolean copyOnSelect() {
        return true;
    }
}
