package cfucli.app;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.*;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import java.awt.image.BufferedImage;
import java.util.List;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

/** Drawn rather than shipped. A prompt chevron and a caret on a dark tile say "terminal" at any
 *  size, and the one accent dot - amber where a machine is being shared, cyan where one is being
 *  watched, white for the tray - is what tells two open windows apart on the taskbar. */
public final class Icons {

    public static final Color
            BACKDROP_TOP = Color.web("#1B2038"),
            BACKDROP_BOTTOM = Color.web("#0D1020"),
            PROMPT = Color.web("#3FD68C"),
            HOST_ACCENT = Color.web("#F2A93B"),
            VIEWER_ACCENT = Color.web("#48C7E8"),
            TRAY_ACCENT = Color.web("#E8EAF2");

    static final int[] SIZES = {16, 24, 32, 48, 64, 128};

    public static List<Image> windowIcons(Color accent) {
        var out = new java.util.ArrayList<Image>(SIZES.length);
        for (var s : SIZES) out.add(render(s, accent));
        return out;
    }

    public static Image render(int size, Color accent) {
        var canvas = new Canvas(size, size);
        draw(canvas.getGraphicsContext2D(), size, accent);
        var params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, new WritableImage(size, size));
    }

    /** Two designs, not one scaled design.
     *  <p>
     *  A 16-pixel tab icon has about sixteen device pixels to work with, and the full mark - tile
     *  border, chevron, caret and a role dot - turns to mush at that size, which is what shipped
     *  first. So below 24px the mark is one fat chevron in the ROLE colour and nothing else: the
     *  whole icon then reads as amber or cyan or white, which tells two open windows apart far
     *  better on a taskbar than a two-pixel dot ever could. */
    static void draw(GraphicsContext g, double s, Color accent) {
        var radius = s * 0.22;
        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, BACKDROP_TOP), new Stop(1, BACKDROP_BOTTOM)));
        g.fillRoundRect(0, 0, s, s, radius, radius);

        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);

        if (s < 24) {
            g.setStroke(accent);
            g.setLineWidth(Math.max(2, s * 0.16));
            g.beginPath();
            g.moveTo(s * 0.30, s * 0.24);
            g.lineTo(s * 0.68, s * 0.50);
            g.lineTo(s * 0.30, s * 0.76);
            g.stroke();
            return;
        }

        g.setStroke(Color.web("#3A4166"));
        g.setLineWidth(Math.max(1, s * 0.03));
        g.strokeRoundRect(s * 0.02, s * 0.02, s * 0.96, s * 0.96, radius, radius);

        g.setStroke(PROMPT);
        g.setLineWidth(Math.max(1.4, s * 0.09));
        g.beginPath();
        g.moveTo(s * 0.26, s * 0.33);
        g.lineTo(s * 0.48, s * 0.51);
        g.lineTo(s * 0.26, s * 0.69);
        g.stroke();

        g.strokeLine(s * 0.58, s * 0.69, s * 0.78, s * 0.69);

        g.setFill(accent);
        var r = s * 0.11;
        g.fillOval(s * 0.72 - r, s * 0.26 - r, r * 2, r * 2);
    }

    /** The tray lives in AWT, so the tile has to cross over. Copying the pixels by hand keeps the
     *  javafx-swing module out of the build for the sake of one conversion. */
    public static BufferedImage awt(int size, Color accent) {
        var fx = render(size, accent);
        var img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var reader = fx.getPixelReader();
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++) img.setRGB(x, y, reader.getArgb(x, y));
        }
        return img;
    }

    private Icons() {}
}
