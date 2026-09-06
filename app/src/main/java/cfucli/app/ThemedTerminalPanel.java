package cfucli.app;

import com.techsenger.jeditermfx.core.model.StyleState;
import com.techsenger.jeditermfx.core.model.TerminalTextBuffer;
import com.techsenger.jeditermfx.ui.TerminalPanel;
import com.techsenger.jeditermfx.ui.settings.SettingsProvider;

/** Exists for one line. The panel re-reads the font from the settings provider in
 *  {@code reinitFontAndResize}, which is protected, so a subclass is the only way to ask for it -
 *  and asking for it is what makes a font size control possible at all. */
public final class ThemedTerminalPanel extends TerminalPanel {

    public ThemedTerminalPanel(SettingsProvider settings, TerminalTextBuffer buffer, StyleState state) {
        super(settings, buffer, state);
    }

    public void refreshFont() {
        reinitFontAndResize();
    }
}
