package cfucli.app;

import module java.base;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import javafx.application.Platform;

/** One tray icon for the machine, however many nodes are running.
 *  <p>
 *  A separate role of the same exe, holding no state at all: it finds nodes by reading the port
 *  files they already write and talks to each over the loopback protocol the cli already uses. So
 *  it can be killed and restarted mid-session without touching anything live, and nodes work
 *  perfectly well with no tray running - which is what keeps the headless and agent paths honest.
 *  <p>
 *  The alternatives are worse in specific ways. A tray icon per node gives Windows N icons to hide
 *  in the overflow. Folding the tray into the first node makes one node special and raises the
 *  question of what happens when that one exits.
 *  <p>
 *  The menu here is deliberately thin. It is AWT, so it cannot show which keys do what, and the
 *  keyboard surface is the manager window - which is also why a click on the icon opens that
 *  rather than a menu. */
public final class TrayRole {

    ManagerWindow manager;
    TrayIcon icon;

    public void start() {
        Platform.setImplicitExit(false);
        manager = new ManagerWindow();
        if (!SystemTray.isSupported()) {
            // No tray on this desktop, so the manager IS the app. Better than exiting silently.
            manager.show();
            manager.stage.setOnCloseRequest(e -> Shutdown.now(null, null));
            return;
        }
        install();
        manager.show();
    }

    void install() {
        var menu = new PopupMenu();
        menu.add(item("Manager", () -> Platform.runLater(manager::show)));
        menu.addSeparator();
        menu.add(item("Share this machine...", () -> spawn("--host")));
        menu.add(item("Connect to a machine...", this::spawn));
        menu.addSeparator();
        menu.add(item("Quit the tray", this::quit));

        icon = new TrayIcon(Icons.awt(16, Icons.TRAY_ACCENT), "cfucli", menu);
        icon.setImageAutoSize(true);
        icon.addActionListener(e -> Platform.runLater(manager::show));
        try {
            SystemTray.getSystemTray().add(icon);
        } catch (java.awt.AWTException e) {
            System.err.println("[cfucli-tray] cannot add the tray icon: " + e.getMessage());
        }
    }

    static MenuItem item(String label, Runnable action) {
        var m = new MenuItem(label);
        m.addActionListener(e -> action.run());
        return m;
    }

    void spawn(String... role) {
        try {
            var args = new ArrayList<>(List.of(role));
            args.addAll(List.of("--node", NodeHost.freeName(AppArgs.DEFAULT_NODE)));
            Relaunch.spawn(args.toArray(String[]::new));
        } catch (RuntimeException e) {
            if (icon != null) icon.displayMessage("cfucli", Dialogs.reason(e), TrayIcon.MessageType.ERROR);
        }
    }

    /** Ends the tray and nothing else. Every session belongs to some other process, and quitting a
     *  manager must never be a way to drop somebody's live shell by accident. */
    void quit() {
        if (icon != null) SystemTray.getSystemTray().remove(icon);
        Shutdown.now(null, null);
    }
}
