package cfucli.app;

import javafx.application.Platform;
import javafx.stage.Stage;
import cfucli.node.WindowControl;

/** Lets the tray, in another process, raise or hide this window over the loopback protocol the cli
 *  already uses.
 *  <p>
 *  The visible flag is mirrored into a volatile rather than read from the Stage, because the
 *  question arrives on a socket thread and JavaFX properties are only safe to read on its own. */
public final class StageControl implements WindowControl {

    final Stage stage;
    volatile boolean up;

    public StageControl(Stage stage) {
        this.stage = stage;
        stage.showingProperty().addListener((o, was, is) -> track());
        stage.iconifiedProperty().addListener((o, was, is) -> track());
        track();
    }

    void track() {
        up = stage.isShowing() && !stage.isIconified();
    }

    @Override
    public void show() {
        Platform.runLater(() -> {
            stage.show();
            stage.setIconified(false);
            stage.toFront();
            stage.requestFocus();
        });
    }

    @Override
    public void hide() {
        Platform.runLater(stage::hide);
    }

    @Override
    public boolean visible() {
        return up;
    }
}
