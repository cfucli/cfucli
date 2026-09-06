package cfucli.app;

import module java.base;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import cfucli.node.NodeClient;
import luvjfx.FxLabel;
import luvjfx.FxTextField;

import static luvjfx.Fx.*;

/** Everything cfucli is doing on this machine, in one list, with the actions on function keys.
 *  <p>
 *  There is a filter box, so by our own keyboard rule every letter and digit belongs to typing and
 *  none of them may be a shortcut. The arrows move the selection, Enter opens, Esc hides, and the
 *  legend along the bottom names every key so nothing has to be looked up elsewhere. */
public final class ManagerWindow {

    static final Duration REFRESH = Duration.ofSeconds(3);

    final Stage stage = new Stage();
    final FxTextField filter = textField($ -> $.promptText("filter by node name or session id"));
    final ListView<NodeCard> list = new ListView<>();
    final FxLabel status = label("").styleClass("card-detail");
    final List<NodeCard> all = new ArrayList<>();

    Timeline ticker;

    public ManagerWindow() {
        build();
    }

    void build() {
        list.setCellFactory(v -> new NodeCardCell());
        list.setPlaceholder(label("nothing is running - F9 shares this machine, F10 connects to one")
                .styleClass("card-detail").node);
        list.setItems(FXCollections.observableArrayList());
        filter.node.textProperty().addListener((o, was, is) -> apply());

        var top = hbox($ -> $.spacing(10)).styleClass("bar").nodes(
                label("cfucli").styleClass("card-title"), filter);
        top.node.getChildren().add(Ui.spacer());
        top.nodes(
                button("F9  Share this machine").onAction(e -> spawn("--host")),
                button("F10  Connect").onAction(e -> spawn()));

        var legend = Ui.legend("F2", "show", "F3", "hide", "F4", "lock", "F5", "view only",
                "F6", "copy id", "F7", "end session", "F8", "stop node", "F12", "refresh", "Esc", "hide");

        var scene = scene(borderPane()
                .top(top)
                .center(vbox($ -> $.spacing(6).padding(8)).nodes(fx(list), status).attr(v -> {
                    javafx.scene.layout.VBox.setVgrow(list, javafx.scene.layout.Priority.ALWAYS);
                }))
                .bottom(legend), 720, 420);
        Ui.dress(scene);
        Keys.functionKeys(scene, () -> true, Map.of(
                KeyCode.F2, () -> act("show", Map.of(), "shown"),
                KeyCode.F3, () -> act("hide", Map.of(), "hidden"),
                KeyCode.F4, () -> toggle("lock", "locked"),
                KeyCode.F5, () -> toggle("viewonly", "viewOnly"),
                KeyCode.F6, this::copyId,
                KeyCode.F7, () -> act("end", Map.of(), "session ended"),
                KeyCode.F8, this::stopNode,
                KeyCode.F9, () -> spawn("--host"),
                KeyCode.F10, () -> spawn(),
                KeyCode.F12, this::refresh));
        Keys.onEscape(scene, stage::hide);
        // The focus lives in the filter box, because typing is what a person does first here. A
        // single-line field has nothing to do with Up and Down, so those move the selection from
        // wherever the focus is - otherwise the only way to reach a row would be the mouse.
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (Keys.plain(e, KeyCode.DOWN)) move(1, e);
            else if (Keys.plain(e, KeyCode.UP)) move(-1, e);
            else if (Keys.plain(e, KeyCode.ENTER)) {
                e.consume();
                act("show", Map.of(), "shown");
            }
        });

        Ui.icons(stage, Icons.TRAY_ACCENT);
        stage.setTitle("cfucli - manager");
        stage.setScene(scene);
        stage.setOnShown(e -> {
            refresh();
            filter.node.requestFocus();
        });

        ticker = new Timeline(new KeyFrame(javafx.util.Duration.millis(REFRESH.toMillis()), e -> refresh()));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
    }

    public void show() {
        stage.show();
        stage.setIconified(false);
        stage.toFront();
        stage.requestFocus();
    }

    public void refresh() {
        if (!stage.isShowing()) return;
        Thread.ofPlatform().name("manager-scan").daemon().start(() -> {
            var found = NodeScan.scan();
            Platform.runLater(() -> {
                all.clear();
                all.addAll(found);
                apply();
            });
        });
    }

    void apply() {
        var needle = filter.node.getText() == null ? "" : filter.node.getText().trim().toLowerCase();
        var selected = list.getSelectionModel().getSelectedItem();
        var shown = all.stream().filter(c -> matches(c, needle)).toList();
        list.getItems().setAll(shown);
        if (selected != null) {
            for (var i = 0; i < shown.size(); i++) {
                if (Objects.equals(shown.get(i).node(), selected.node())) {
                    list.getSelectionModel().select(i);
                    return;
                }
            }
        }
        if (!shown.isEmpty() && list.getSelectionModel().isEmpty()) list.getSelectionModel().select(0);
    }

    static boolean matches(NodeCard c, String needle) {
        if (needle.isEmpty()) return true;
        var haystack = (c.node() + " " + c.role() + " " + c.sessionId() + " " + c.prettyId() + " " + c.detail())
                .toLowerCase();
        return haystack.contains(needle);
    }

    void move(int by, javafx.scene.input.KeyEvent e) {
        if (list.getItems().isEmpty()) return;
        e.consume();
        var next = list.getSelectionModel().getSelectedIndex() + by;
        list.getSelectionModel().select(Math.max(0, Math.min(list.getItems().size() - 1, next)));
        list.scrollTo(list.getSelectionModel().getSelectedIndex());
    }

    NodeCard selected() {
        var c = list.getSelectionModel().getSelectedItem();
        if (c == null) status.text("pick a node first - the arrow keys move the selection");
        return c;
    }

    void act(String verb, Map<String, Object> args, String said) {
        var c = selected();
        if (c == null) return;
        try {
            var r = new NodeClient(c.node()).call(verb, args, Duration.ofSeconds(10));
            if (r != null && r.path("window").isBoolean() && !r.path("window").asBoolean()) {
                status.text(r.path("detail").asText("that node has no window"));
            } else {
                status.text(c.node() + ": " + said);
            }
        } catch (RuntimeException e) {
            status.text(c.node() + ": " + Dialogs.reason(e));
        }
        refresh();
    }

    void toggle(String verb, String field) {
        var c = selected();
        if (c == null) return;
        var now = "locked".equals(field) ? Boolean.TRUE.equals(c.locked()) : Boolean.TRUE.equals(c.viewOnly());
        act(verb, Map.of("value", !now), (now ? "un" : "") + field.toLowerCase() + " set");
    }

    void copyId() {
        var c = selected();
        if (c == null) return;
        if (c.sessionId() == null || c.sessionId().isBlank()) {
            status.text(c.node() + " has no session to copy");
            return;
        }
        Ui.copy(c.sessionId());
        status.text("copied " + c.prettyId());
    }

    void stopNode() {
        var c = selected();
        if (c == null) return;
        var answer = Dialogs.choose(stage, "Stop node '" + c.node() + "'?",
                "Stopping a node ends any session it holds and closes its window. The shell on the "
                + "far machine keeps running; this end simply stops watching it.",
                List.of("Keep it running", "Stop the node"), Icons.TRAY_ACCENT);
        if (answer != 1) return;
        act("shutdown", Map.of(), "stopping");
    }

    /** No role means the launcher, which is the right surface for connecting: it has the boxes for
     *  the id and the password that a bare --join would have nowhere to read from. */
    void spawn(String... role) {
        try {
            var args = new ArrayList<>(List.of(role));
            args.addAll(List.of("--node", NodeHost.freeName(AppArgs.DEFAULT_NODE)));
            Relaunch.spawn(args.toArray(String[]::new));
            status.text("starting a new window");
        } catch (RuntimeException e) {
            status.text(Dialogs.reason(e));
        }
    }
}
