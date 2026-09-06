package cfucli.app;

import module java.base;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/** Renders every icon size and role side by side, at 1:1 and magnified, so the small ones can be
 *  judged rather than assumed. The first design here looked fine at 128px and was mush at 16.
 *  <p>
 *  mvn -o exec:java -Dexec.mainClass=cfucli.app.IconSheet -Dexec.classpathScope=test
 */
public final class IconSheet {

    public static void main(String[] args) throws Exception {
        var ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        ready.await();
        Platform.runLater(IconSheet::show);
        Thread.sleep(60_000);
        System.exit(0);
    }

    static void show() {
        var roles = List.of(Icons.HOST_ACCENT, Icons.VIEWER_ACCENT, Icons.TRAY_ACCENT);
        var sizes = List.of(16, 24, 32, 48, 64, 128);
        var canvas = new Canvas(760, 3 * 150);
        var g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#7A7F94"));
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        for (var r = 0; r < roles.size(); r++) {
            var x = 12.0;
            var y = 12.0 + r * 150;
            for (var size : sizes) {
                var image = Icons.render(size, roles.get(r));
                g.drawImage(image, x, y);
                g.drawImage(image, x, y + 34, size * 4.0, size * 4.0);
                x += Math.max(size, size * 4.0) + 14;
            }
        }
        var stage = new Stage();
        stage.setTitle("icon-sheet");
        stage.setScene(new javafx.scene.Scene(new javafx.scene.layout.Pane(canvas)));
        stage.show();
    }

    private IconSheet() {}
}
