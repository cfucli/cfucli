package cfucli.app;

import module java.base;
import javafx.scene.input.KeyCode;
import javafx.stage.*;
import cfucli.relay.Ids;

import static luvjfx.Fx.*;

/** The two things a person has to read out over the phone, big enough to read out.
 *  <p>
 *  Behind a keypress rather than always on screen, because a shared shell is exactly the kind of
 *  window that ends up in a screen recording, and the password should not be sitting in the frame
 *  for the whole session. */
public final class SessionDetails {

    public static void show(Window owner, String sessionId, String password, String transport,
                            String node, int port, javafx.scene.paint.Color accent) {
        var stage = new Stage(StageStyle.UTILITY);
        var revealed = new AtomicBoolean(password == null);
        var secret = label(hidden(password)).styleClass("mono");

        var rows = vbox($ -> $.spacing(4)).nodes(
                label("Session id").styleClass("subtitle"),
                label(Ids.prettySessionId(sessionId)).styleClass("mono"),
                label(" ").styleClass("subtitle"),
                label(password == null ? "One-time password" : "One-time password  (F5 to reveal)").styleClass("subtitle"),
                secret);

        var buttons = hbox($ -> $.spacing(9)).nodes(
                button("F4  Copy id").onAction(e -> Ui.copy(sessionId)),
                button("F5  Copy password").disable(password == null)
                        .onAction(e -> Ui.copy(password)),
                button("Close").onAction(e -> stage.close()));

        var root = vbox($ -> $.spacing(14).padding(18)).nodes(
                label("Session details").styleClass("title"),
                rows,
                label(" ").styleClass("subtitle"),
                label("transport  " + transport).styleClass("card-detail"),
                label("node  " + node + "   ·   cli port  " + port).styleClass("card-detail"),
                FxConsentGate.wrapped("The cli reaches this session with:  cfucli exec \"whoami\" --node " + node),
                buttons);

        var scene = scene(root);
        Ui.dress(scene);
        Keys.onEscape(scene, stage::close);
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (Keys.plain(e, KeyCode.F4)) {
                e.consume();
                Ui.copy(sessionId);
            } else if (Keys.plain(e, KeyCode.F5) && password != null) {
                e.consume();
                if (revealed.compareAndSet(false, true)) secret.text(Ids.prettyPassword(password));
                else Ui.copy(password);
            }
        });

        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        Ui.icons(stage, accent);
        stage.setTitle("cfucli - session details");
        stage.setScene(scene);
        stage.showAndWait();
    }

    static String hidden(String password) {
        return password == null ? "held by the host" : "•".repeat(Ids.PASSWORD_CHARS + 1);
    }

    private SessionDetails() {}
}
