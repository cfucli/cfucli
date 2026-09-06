package cfucli.app;

import module java.base;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import cfucli.node.RemoteState;
import cfucli.node.ViewerSession;
import cfucli.relay.Ids;
import luvjfx.FxLabel;

import static luvjfx.Fx.*;

/** The mirror, on the machine that is driving. Typing here goes to the far shell and comes back as
 *  its own echo, so what appears is what the far end actually did rather than what was sent.
 *  <p>
 *  It is also where the agent's work shows up: the cli talks to this same node, so a command
 *  claude-code runs is announced into this view as it happens. */
public final class ViewerWindow {

    final Stage stage;
    final NodeHost host;
    final ViewerSession session;
    final TerminalView view;
    final AtomicBoolean ownsFunctionKeys = new AtomicBoolean(true), sized = new AtomicBoolean();

    final FxLabel state = Ui.pill("connected", "pill-live");
    final FxLabel where = label("").styleClass("card-detail");
    final FxLabel flags = Ui.pill("", "pill-locked").visible(false);
    final FxLabel legendTail = label("").styleClass("label");
    final FxLabel logHint = label("").styleClass("label", "hint");
    final LogPane log = new LogPane();
    final javafx.scene.control.SplitPane split = new javafx.scene.control.SplitPane();

    public ViewerWindow(Stage stage, NodeHost host) {
        this.stage = stage;
        this.host = host;
        this.session = host.service().viewer();
        var remote = session.remote();
        this.view = new TerminalView(new ViewerTty(session), remote.columns(), remote.rows(), Fonts.remembered(host));
        session.snapshot(view::screen);
        session.onChange(() -> Platform.runLater(this::refresh));
        session.onActivity(line -> Platform.runLater(() -> {
            log.add(line);
            refresh();
        }));
        build();
    }

    void build() {
        var bar = hbox($ -> $.spacing(10)).styleClass("bar").nodes(
                state,
                label("session " + Ids.prettySessionId(session.sessionId())).styleClass("mono"),
                where, flags);
        bar.node.getChildren().add(Ui.spacer());
        bar.nodes(
                button("F1  Details").onAction(e -> details()),
                button("F10  Match host size").onAction(e -> matchHost()),
                button("F9  Leave").styleClass("danger").onAction(e -> leaveAndClose()));

        var legend = Ui.legend("F4", "copy id", "F6 F7", "font size", "F8", "clear this view",
                "F12", "activity log", "F11", "keys to shell");
        legend.add(legendTail);
        legend.add(logHint);

        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.getItems().add(view.pane());
        var scene = scene(borderPane().top(bar).center(split).bottom(legend));
        Ui.dress(scene);
        Keys.functionKeys(scene, ownsFunctionKeys::get, Map.of(
                KeyCode.F1, this::details,
                KeyCode.F4, () -> Ui.copy(session.sessionId()),
                KeyCode.F6, () -> Fonts.step(host, view, -TerminalView.FONT_STEP, this::refresh),
                KeyCode.F7, () -> Fonts.step(host, view, TerminalView.FONT_STEP, this::refresh),
                KeyCode.F8, view::clear,
                KeyCode.F9, this::leaveAndClose,
                KeyCode.F10, this::matchHost,
                KeyCode.F12, this::toggleLog));
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!Keys.plain(e, KeyCode.F11)) return;
            e.consume();
            ownsFunctionKeys.set(!ownsFunctionKeys.get());
            refresh();
        });

        Ui.icons(stage, Icons.VIEWER_ACCENT);
        stage.setTitle("cfucli - watching " + Ids.prettySessionId(session.sessionId()) + "  ·  " + host.node());
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            e.consume();
            leaveAndClose();
        });
        refresh();
        stage.show();
        view.focus();
    }

    /** Snaps this window back to the geometry the host reports. The host is authoritative about
     *  size, so a viewer that has been dragged to some other shape is showing reflowed output; one
     *  key puts it back rather than leaving the person to guess at pixels.
     *  <p>
     *  Measured from the cells already on screen rather than from a font metric, because the cell
     *  size after layout is the only number that is certainly the one being used. */
    void matchHost() {
        var remote = session.remote();
        var pane = view.pane();
        if (!remote.known() || pane.getWidth() <= 0 || view.columns() <= 0 || view.rows() <= 0) {
            stage.sizeToScene();
            return;
        }
        var cellWidth = pane.getWidth() / view.columns();
        var cellHeight = pane.getHeight() / view.rows();
        stage.setWidth(stage.getWidth() + (remote.columns() - view.columns()) * cellWidth);
        stage.setHeight(stage.getHeight() + (remote.rows() - view.rows()) * cellHeight);
    }

    /** A split rather than a tab, so the mirror stays in sight while the log is open. */
    void toggleLog() {
        if (log.showing()) {
            split.getItems().remove(log.node());
            log.showing(false);
        } else {
            split.getItems().add(log.node());
            log.showing(true);
            split.setDividerPositions(0.72);
        }
        refresh();
        view.focus();
    }

    void details() {
        SessionDetails.show(stage, session.sessionId(), null,
                host.service().dispatch("status", null).path("transport").asText("unknown"),
                host.node(), host.port(), Icons.VIEWER_ACCENT);
    }

    void refresh() {
        var remote = session.remote();
        var live = !session.closed();
        // The host's geometry only arrives with its first STATE frame, which is after this window
        // was built. Matching it once, unasked, is what stops the mirror opening the wrong shape.
        if (remote.known() && sized.compareAndSet(false, true)) matchHost();
        state.text(live ? "connected" : "left").styleClass("pill", live ? "pill-live" : "pill-wait");
        where.text((remote.hostName() == null ? "the host" : remote.hostName())
                   + "  ·  " + (remote.shell() == null ? "a shell" : remote.shell())
                   + "  ·  " + remote.columns() + "x" + remote.rows()
                   + "  ·  " + Math.round(view.fontSize()) + "pt"
                   + (sizeMatches(remote) ? "" : "  ·  this window is " + view.columns() + "x" + view.rows()));
        var restricted = remote.locked() ? "the host has locked you out"
                : remote.viewOnly() ? "view only - you cannot type" : null;
        flags.visible(restricted != null).text(restricted == null ? "" : restricted)
                .styleClass("pill", remote.locked() ? "pill-locked" : "pill-view");
        legendTail.text(ownsFunctionKeys.get() ? "" : "the shell has the function keys - F11 takes them back");
        logHint.text(log.hint()).styleClass("label", log.loud() ? "hint-loud" : "hint");
    }

    boolean sizeMatches(RemoteState remote) {
        return view.columns() == remote.columns() && view.rows() == remote.rows();
    }

    void leaveAndClose() {
        Shutdown.now(host, view);
    }
}
