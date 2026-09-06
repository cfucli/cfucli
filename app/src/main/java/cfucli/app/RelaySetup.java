package cfucli.app;

import module java.base;
import javafx.scene.input.KeyCode;
import javafx.stage.*;
import cfucli.relay.RelayBlob;
import cfucli.relay.SettingsStore;

import static luvjfx.Fx.*;

/** Setting this machine up to reach a relay, without a terminal.
 *  <p>
 *  A copy that shipped without credentials works on its own machine and cannot reach anybody, and
 *  the person who needs to change that is usually the one least likely to be given a command line.
 *  So the whole task is: paste what you were sent, press Save. The parsing is the same
 *  {@link RelayBlob} the cli uses, which is what lets the sender paste whatever they have to hand -
 *  console lines, a settings.toml, either quoting style - rather than a shape nobody was told. */
public final class RelaySetup {

    public static void show(Window owner, javafx.scene.paint.Color accent, Runnable afterSave) {
        var stage = new Stage(StageStyle.UTILITY);
        var settings = SettingsStore.load();

        var status = label(describe()).styleClass("card-detail");
        var box = textArea($ -> $.prefRowCount(7).wrapText(true)
                .promptText("Paste the lines you were sent, for example:\n\n"
                            + "REDIS_URL = rediss://...\n"
                            + "UPSTASH_REDIS_REST_URL = https://...\n"
                            + "UPSTASH_REDIS_REST_TOKEN = ..."));
        var found = label("").styleClass("card-detail");

        // Re-read on every keystroke so the dialog says what it understood BEFORE anything is
        // saved. Pasting a secret and being told only afterwards that it was not recognised is
        // the moment people give up on a setup screen.
        box.node.textProperty().addListener((o, was, now) -> found.text(summarise(now)));

        var save = button("F5  Save").styleClass("primary").onAction(e -> {
            var keys = RelayBlob.parse(box.node.getText());
            if (keys.isEmpty()) {
                Dialogs.error(stage, "Nothing recognised in that",
                        "Paste needs to contain at least one of REDIS_URL, UPSTASH_REDIS_REST_URL or"
                        + " UPSTASH_REDIS_REST_TOKEN. Copy the whole block you were sent, including"
                        + " the names on the left of the = sign.");
                return;
            }
            SettingsStore.update(s -> keys.forEach((k, v) -> RelayBlob.apply(s, k, v)));
            status.text(describe());
            box.text("");
            Dialogs.info(stage, "Saved",
                    "This machine is now pointed at that relay. Anything already running still holds"
                    + " the old settings, so close the session window and start it again.");
            if (afterSave != null) afterSave.run();
        });

        var buttons = hbox($ -> $.spacing(9)).nodes(
                button("F4  Paste").onAction(e -> {
                    var cb = javafx.scene.input.Clipboard.getSystemClipboard();
                    if (cb.hasString()) box.text(cb.getString());
                }),
                save,
                button("Esc  Close").onAction(e -> stage.close()));

        var root = vbox($ -> $.spacing(12).padding(18)).nodes(
                label("Connect this machine to a relay").styleClass("title"),
                label("A relay is what lets this machine be reached from somewhere else. Without one,"
                      + " everything here still works - but only on this machine.")
                        .styleClass("subtitle"),
                status,
                label("Pasting values below replaces whatever is set.").styleClass("card-detail"),
                label(" ").styleClass("subtitle"),
                box, found, buttons);

        var scene = scene(root);
        Ui.dress(scene);
        Keys.functionKeys(scene, () -> true, Map.of(
                KeyCode.F4, () -> {
                    var cb = javafx.scene.input.Clipboard.getSystemClipboard();
                    if (cb.hasString()) box.text(cb.getString());
                },
                KeyCode.F5, () -> save.node.fire()));
        Keys.onEscape(scene, stage::close);

        Ui.icons(stage, accent);
        stage.setTitle("cfucli - relay setup");
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setScene(scene);
        stage.show();
        box.node.requestFocus();
    }

    /** Says whether it is set, never what it is. A settings screen is a thing people screen-share
     *  while asking for help. */
    static String describe() {
        var s = SettingsStore.load();
        var native_ = notBlank(s.redisUrl());
        var rest = notBlank(s.restUrl()) && notBlank(s.restToken());
        if (!native_ && !rest) return "Not connected to any relay - this machine is on its own.";
        return "Connected to a relay" + (native_ && rest ? " (both the direct and the fallback route)"
                : native_ ? " (direct route only)" : " (fallback route only)") + ".";
    }

    static String summarise(String text) {
        var keys = RelayBlob.parse(text == null ? "" : text);
        if (text == null || text.isBlank()) return "";
        if (keys.isEmpty()) return "Nothing recognised yet - the names on the left of the = sign have to be there.";
        return "Recognised: " + String.join(", ", keys.keySet());
    }

    static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private RelaySetup() {}
}
