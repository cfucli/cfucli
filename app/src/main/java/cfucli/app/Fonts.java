package cfucli.app;

import cfucli.relay.SettingsStore;

/** Remembers the terminal font size across runs.
 *  <p>
 *  It belongs in the settings file rather than in the window, because the size a person can
 *  comfortably read is a property of that person and their screen, not of one session - having to
 *  set it again on every launch is the sort of small friction that makes a tool feel unfinished. */
public final class Fonts {

    public static double remembered(NodeHost host) {
        var configured = host.service().settings().terminalFontSize();
        return configured == null ? TerminalTheme.DEFAULT_SIZE : configured;
    }

    /** Nudges the size, applies it, saves it, and tells the caller to redraw the chrome that
     *  reports it. Saving is best effort: a read-only settings file must not stop the font from
     *  changing for this session. */
    public static void step(NodeHost host, TerminalView view, double delta, Runnable after) {
        var applied = view.fontSize(view.fontSize() + delta);
        host.service().settings().terminalFontSize(applied);
        try {
            SettingsStore.update(s -> s.terminalFontSize(applied));
        } catch (RuntimeException ignored) {
        }
        if (after != null) after.run();
    }

    private Fonts() {}
}
