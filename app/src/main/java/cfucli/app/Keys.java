package cfucli.app;

import module java.base;
import java.util.function.BooleanSupplier;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/** The one keyboard rule this app does not break: a bare letter or digit belongs to whatever the
 *  user is typing, never to a control. In a terminal window that claims every letter, every digit,
 *  Esc and Tab - so the actions get function keys and nothing else.
 *  <p>
 *  JavaFX breaks the rule by default. A {@code _} in a Button or CheckBox label registers a
 *  mnemonic, and the handler fires mnemonics when {@code isAltDown() || mnemonicsDisplayEnabled} -
 *  and that second flag latches on at the first Alt press in the Scene and is never reliably
 *  cleared. From then on the bare letter fires the mnemonic and never reaches the terminal.
 *  {@link #noMnemonics} is applied to every root here for that reason. */
public final class Keys {

    /** Strips mnemonics from a whole tree, tab contents included - they are built lazily, so a
     *  plain child walk misses them. */
    public static void noMnemonics(Node node) {
        if (node instanceof Labeled l) l.setMnemonicParsing(false);
        if (node instanceof TabPane tabs) {
            tabs.getTabs().forEach(t -> {
                if (t.getContent() != null) noMnemonics(t.getContent());
            });
        }
        if (node instanceof Parent p) p.getChildrenUnmodifiable().forEach(Keys::noMnemonics);
    }

    public static boolean plain(KeyEvent e, KeyCode code) {
        return e.getCode() == code
               && !e.isAltDown() && !e.isControlDown() && !e.isMetaDown() && !e.isShiftDown();
    }

    /** Function-key actions, bound as a scene filter so they run before any control - including the
     *  terminal - sees the key.
     *  <p>
     *  {@code active} is the escape hatch. A shared shell may well be running something that wants
     *  the function keys itself, and the window handing them back on request is cheaper than
     *  arguing about who owns F3. */
    public static void functionKeys(Scene scene, BooleanSupplier active, Map<KeyCode, Runnable> actions) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!active.getAsBoolean()) return;
            var action = actions.get(e.getCode());
            if (action == null || !plain(e, e.getCode())) return;
            e.consume();
            action.run();
        });
    }

    /** For a window with no terminal in it, where Esc can safely mean cancel. */
    public static void onEscape(Scene scene, Runnable action) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!plain(e, KeyCode.ESCAPE)) return;
            e.consume();
            action.run();
        });
    }

    private Keys() {}
}
