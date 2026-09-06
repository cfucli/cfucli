package cfucli.app;

import module java.base;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Stage;
import luvjfx.FxHBox;
import luvjfx.FxLabel;

import static luvjfx.Fx.*;

/** Shared chrome. The legend along the bottom is not decoration: a key you have to look up
 *  somewhere else is read the long way round every time, which is how the DOS-era tools stayed
 *  drivable by people who never opened a manual. */
public final class Ui {

    public static final String STYLESHEET = "/cfucli/app/app.css";

    public static void dress(Scene scene) {
        scene.getStylesheets().add(Ui.class.getResource(STYLESHEET).toExternalForm());
        Keys.noMnemonics(scene.getRoot());
    }

    public static void icons(Stage stage, javafx.scene.paint.Color accent) {
        stage.getIcons().setAll(Icons.windowIcons(accent));
    }

    /** One legend entry per pair: the key, then what it does. */
    public static FxHBox legend(String... keyThenLabel) {
        var bar = hbox().styleClass("legend");
        for (var i = 0; i + 1 < keyThenLabel.length; i += 2) {
            bar.add(hbox($ -> $.spacing(4)).nodes(
                    label(keyThenLabel[i]).styleClass("key"),
                    label(keyThenLabel[i + 1])));
        }
        return bar;
    }

    public static FxLabel pill(String text, String variant) {
        return label(text).styleClass("pill", variant);
    }

    public static void copy(String text) {
        var c = new ClipboardContent();
        c.putString(text == null ? "" : text);
        Clipboard.getSystemClipboard().setContent(c);
    }

    public static Label grow(Label l) {
        javafx.scene.layout.HBox.setHgrow(l, javafx.scene.layout.Priority.ALWAYS);
        return l;
    }

    public static javafx.scene.Node spacer() {
        var r = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(r, javafx.scene.layout.Priority.ALWAYS);
        return r;
    }

    private Ui() {}
}
