package cfucli.app;

import module java.base;
import java.util.function.Consumer;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import cfucli.relay.SettingsStore;
import luvjfx.FxTextField;

import static luvjfx.Fx.*;

/** What a person sees when they double-click the exe: which end am I?
 *  <p>
 *  This window has text boxes in it, so every letter and digit belongs to typing and the two
 *  actions get function keys. Enter commits whichever form the focus is in. */
public final class LauncherWindow {

    static final List<String> SHELLS = List.of("cmd.exe", "powershell.exe", "pwsh.exe");

    final Stage stage;
    final AppArgs args;
    final Consumer<AppArgs> go;

    final FxTextField nodeField;
    final FxTextField sessionField = textField($ -> $.promptText("paste the invite").prefColumnCount(22));
    final luvjfx.FxPasswordField passwordField = passwordField($ -> $.promptText("one-time password"));
    final luvjfx.FxComboBox<String> shellBox = comboBox(String.class);
    final luvjfx.FxLabel relayStatus = label("").styleClass("card-detail");

    public LauncherWindow(Stage stage, AppArgs args, Consumer<AppArgs> go) {
        this.stage = stage;
        this.args = args;
        this.go = go;
        this.nodeField = textField($ -> $.text(NodeHost.freeName(args.node())).prefColumnCount(12));
        build();
    }

    void build() {
        var settings = SettingsStore.load();
        shellBox.node.getItems().setAll(SHELLS);
        shellBox.node.setEditable(true);
        shellBox.node.setValue(args.shell() != null ? args.shell()
                : settings.defaultShell() == null ? SHELLS.getFirst() : settings.defaultShell());
        if (args.sessionId() != null) sessionField.text(args.sessionId());
        if (args.password() != null) passwordField.text(args.password());
        splitPastedInvite();
        refreshRelayStatus();

        var share = vbox($ -> $.spacing(7).padding(14).styleClass("bar")).nodes(
                label("Share this machine").styleClass("card-title"),
                label("Runs a shell here, then F4 copies one invite string for you to send to whoever is joining.")
                        .styleClass("card-detail"),
                hbox($ -> $.spacing(8)).nodes(
                        label("shell").styleClass("subtitle"),
                        shellBox,
                        button("F2  Start sharing").styleClass("primary").onAction(e -> host())));

        // Said here, before anything is attempted. A copy that shipped without credentials will
        // start a session quite happily and simply be unreachable, and the person finds out only
        // when the far end cannot connect - by which time they are debugging the wrong thing.
        var relayRow = hbox($ -> $.spacing(8)).nodes(
                relayStatus,
                button("F5  Relay setup").onAction(e -> relaySetup()));

        var connect = vbox($ -> $.spacing(7).padding(14).styleClass("bar")).nodes(
                label("Connect to a shared machine").styleClass("card-title"),
                label("Mirrors the far shell here. You can type into it, and so can claude-code "
                      + "through the cli.").styleClass("card-detail"),
                hbox($ -> $.spacing(8)).nodes(
                        label("invite").styleClass("subtitle"), sessionField,
                        label("password").styleClass("subtitle"), passwordField,
                        button("F3  Connect").styleClass("primary").onAction(e -> join())));

        var nodeRow = hbox($ -> $.spacing(8)).nodes(
                label("node name").styleClass("subtitle"),
                nodeField,
                label("this is the name claude-code passes to --node").styleClass("card-detail"));

        var root = vbox($ -> $.spacing(12).padding(16)).nodes(
                label("cfucli").styleClass("title"),
                label("A shared terminal across the internet, with no inbound port and no admin rights.")
                        .styleClass("subtitle"),
                share, connect, relayRow, nodeRow);

        var scene = scene(root);
        Ui.dress(scene);
        Keys.functionKeys(scene, () -> true, Map.of(
                KeyCode.F2, this::host,
                KeyCode.F3, this::join,
                KeyCode.F4, this::manager,
                KeyCode.F5, this::relaySetup));
        Keys.onEscape(scene, stage::close);
        sessionField.node.setOnAction(e -> join());
        passwordField.node.setOnAction(e -> join());

        Ui.icons(stage, Icons.TRAY_ACCENT);
        stage.setTitle("cfucli");
        stage.setScene(scene);
        var legend = Ui.legend("F2", "share this machine", "F3", "connect to one",
                "F4", "manager", "F5", "relay setup", "Esc", "close");
        ((javafx.scene.layout.VBox) scene.getRoot()).getChildren().add(legend.node);
        stage.show();
    }

    void host() {
        var shell = shellBox.node.getValue();
        go.accept(new AppArgs(AppRole.HOST, node(), shell == null || shell.isBlank() ? null : shell.trim(), null, null, false));
    }

    /** The other end copies one string, so this end must accept one string. Whatever is pasted into
     *  the first box is watched: the moment it carries a whole invite it is torn into the two boxes,
     *  which is both the thing that works and the thing that shows the person what they pasted.
     *  Typing a bare id is untouched, because a bare id never parses as an invite. */
    void splitPastedInvite() {
        sessionField.node.textProperty().addListener((o, was, now) -> {
            if (now == null || now.isBlank()) return;
            var invited = cfucli.relay.Invite.parse(now);
            if (invited.isEmpty()) return;
            var it = invited.get();
            if (cfucli.relay.Ids.normalise(now).equals(it.sessionId())) return;
            sessionField.text(cfucli.relay.Ids.prettySessionId(it.sessionId()));
            passwordField.text(it.password());
        });
    }

    void join() {
        var id = cfucli.relay.Ids.normalise(sessionField.node.getText());
        var pw = cfucli.relay.Ids.normalise(passwordField.node.getText());
        if (!cfucli.relay.Ids.isSessionId(id)) {
            Dialogs.error(stage, "That is not an invite",
                    "Paste the whole string the other end copied with F4, or type the nine-digit id"
                    + " and the password separately. Spaces and dashes are fine either way.");
            sessionField.node.requestFocus();
            return;
        }
        if (pw == null || pw.isBlank()) {
            Dialogs.error(stage, "The password is missing",
                    "The host window shows a one-time password next to the id. It works once, for this session.");
            passwordField.node.requestFocus();
            return;
        }
        go.accept(new AppArgs(AppRole.JOIN, node(), null, id, pw, false));
    }

    void relaySetup() {
        RelaySetup.show(stage, Icons.TRAY_ACCENT, this::refreshRelayStatus);
    }

    void refreshRelayStatus() {
        relayStatus.text(RelaySetup.describe());
    }

    void manager() {
        go.accept(new AppArgs(AppRole.TRAY, node(), null, null, null, false));
    }

    String node() {
        var n = nodeField.node.getText();
        return n == null || n.isBlank() ? AppArgs.DEFAULT_NODE : n.trim();
    }
}
