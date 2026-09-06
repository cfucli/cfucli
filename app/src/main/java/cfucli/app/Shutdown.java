package cfucli.app;

import javafx.application.Platform;

/** Closing the window is how a person stops this, so it has to actually stop.
 *  <p>
 *  The order matters. The session ends first, which tells the relay and locks the remote out for
 *  good; then the node's socket goes, which removes the port file the tray and the cli discover it
 *  by; then the toolkit. Leaving the port file behind is what makes a later run refuse to start
 *  with a name it should have been able to use. */
public final class Shutdown {

    public static void now(NodeHost host, TerminalView view) {
        try {
            if (view != null) view.close();
        } catch (RuntimeException ignored) {
        }
        try {
            if (host != null) host.close();
        } catch (RuntimeException ignored) {
        }
        Platform.exit();
        // pty4j and the relay both keep non-daemon threads alive in some states, and a window a
        // person closed must not leave a process behind in the task manager.
        new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
            }
            System.exit(0);
        }, "exit-guard").start();
    }

    private Shutdown() {}
}
