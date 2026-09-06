package cfucli.app;

import com.techsenger.jeditermfx.core.model.StyleState;
import com.techsenger.jeditermfx.core.model.TerminalTextBuffer;
import com.techsenger.jeditermfx.ui.JediTermFxWidget;
import com.techsenger.jeditermfx.ui.TerminalPanel;
import com.techsenger.jeditermfx.ui.settings.SettingsProvider;

/** The widget, with the panel swapped for one that will re-read the font on request. */
public final class ThemedTerminalWidget extends JediTermFxWidget {

    public ThemedTerminalWidget(int columns, int rows, SettingsProvider settings) {
        super(columns, rows, settings);
    }

    @Override
    protected TerminalPanel createTerminalPanel(SettingsProvider settings, StyleState state, TerminalTextBuffer buffer) {
        return new ThemedTerminalPanel(settings, buffer, state);
    }

    public ThemedTerminalPanel themedPanel() {
        return (ThemedTerminalPanel) getTerminalPanel();
    }
}
