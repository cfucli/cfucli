package cfucli.app;

import module java.base;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.stage.*;

import static luvjfx.Fx.*;

/** Small modal answers. Not javafx.scene.control.Alert: that arrives with the platform's own light
 *  theme and no way to say which key does what, and a window that cannot wear its shortcuts is
 *  half a keyboard UI. */
public final class Dialogs {

    public static void error(Window owner, String title, String message) {
        choose(owner, title, message, List.of("Close"), Icons.HOST_ACCENT);
    }

    public static void info(Window owner, String title, String message) {
        choose(owner, title, message, List.of("OK"), Icons.TRAY_ACCENT);
    }

    /** Returns the index of the chosen option, or -1 if the window was simply closed. The first
     *  option is the safe one and takes the focus, so Enter never does the destructive thing. */
    public static int choose(Window owner, String title, String message, List<String> options,
                             javafx.scene.paint.Color accent) {
        var answer = new AtomicInteger(-1);
        var stage = new Stage(StageStyle.UTILITY);
        var buttons = hbox($ -> $.spacing(9));
        var first = (Button) null;
        for (var i = 0; i < options.size(); i++) {
            var index = i;
            var b = button(options.get(i)).styleClass(i == 0 ? "primary" : "").onAction(e -> {
                answer.set(index);
                stage.close();
            });
            buttons.add(b);
            if (first == null) first = b.node;
        }
        var root = vbox($ -> $.spacing(12).padding(18)).nodes(
                label(title).styleClass("title"),
                FxConsentGate.wrapped(message),
                buttons);
        var scene = scene(root);
        Ui.dress(scene);
        Keys.onEscape(scene, stage::close);
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        Ui.icons(stage, accent);
        stage.setTitle("cfucli");
        stage.setScene(scene);
        var focus = first;
        stage.setOnShown(e -> {
            if (focus != null) focus.requestFocus();
        });
        stage.showAndWait();
        return answer.get();
    }

    /** A one-line reason a thing failed, without the exception class name people cannot act on. */
    public static String reason(Throwable e) {
        var root = e;
        while (root.getCause() != null && (root.getMessage() == null || root.getMessage().isBlank())) {
            root = root.getCause();
        }
        var m = root.getMessage();
        return m == null || m.isBlank() ? root.getClass().getSimpleName() : m;
    }

    private Dialogs() {}
}
