package cfucli.app;

import javafx.application.Platform;
import javafx.stage.Window;

/** Ends the process once nothing in it is left for a person to use.
 *  <p>
 *  Implicit exit is off in this app, because closing a session window has to end a session first
 *  and that is done explicitly (Shutdown). The cost was a launcher closed with Esc or its X: the
 *  window went, the process stayed, invisible, until the next reboot. So a window that can be the
 *  last thing open calls this when it hides. Checked a moment later, because a launcher that hands
 *  over to a session window hides around the time that window first shows. */
public final class Idle {

    public static void exitIfNothingLeft() {
        Platform.runLater(() -> {
            if (TrayRole.started) return;
            if (Window.getWindows().stream().anyMatch(Window::isShowing)) return;
            Shutdown.now(null, null);
        });
    }

    private Idle() {}
}
