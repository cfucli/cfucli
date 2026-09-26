package cfucli.app;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.*;
import javafx.scene.shape.FillRule;
import java.awt.image.BufferedImage;
import java.util.List;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

/** The organisation's seal - the vermilion carved seal with a terminal prompt cut from it, the
 *  same mark as on cfucli.github.io - drawn from its own SVG path data rather than shipped as
 *  bitmaps, so it is crisp at every size the taskbar, the title bar and the tray ask for.
 *  <p>
 *  The accent dot - amber where a machine is being shared, cyan where one is being watched, white
 *  for the tray - is what tells two open windows apart. It is drawn from 32px up only: below that
 *  a dot is two pixels of noise, and at 16px the seal alone has to be legible. */
public final class Icons {

    public static final Color
            HOST_ACCENT = Color.web("#F2A93B"),
            VIEWER_ACCENT = Color.web("#48C7E8"),
            TRAY_ACCENT = Color.web("#E8EAF2");

    /** Copied from assets/cfucli-logo.svg in the site repo, viewBox 0 0 1024 1024, even-odd fill:
     *  the outer seal with its chipped corners, the chevron, and the cursor block cut out of it. */
    static final String SEAL = "M126 64H900L918 82H960V898L940 918V960H124L104 940H64V126L84 106V64H126Z"
                               + "M230 370H330L560 512L330 654H230L430 512L230 370Z"
                               + "M628 428H800V596H628V428Z";
    static final Color SEAL_RED = Color.web("#B72A22"), RING = Color.web("#0D1020");

    static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

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

    static void draw(GraphicsContext g, double s, Color accent) {
        g.save();
        g.scale(s / 1024.0, s / 1024.0);
        g.setFill(SEAL_RED);
        g.setFillRule(FillRule.EVEN_ODD);
        g.beginPath();
        g.appendSVGPath(SEAL);
        g.fill();
        g.restore();

        if (s < 32) return;
        // Bottom right, over the seal's chipped corner, with a dark ring so it reads against the
        // red and against whatever colour the taskbar happens to be.
        var r = s * 0.085;
        var cx = s * 0.86;
        var cy = s * 0.86;
        var ring = r + Math.max(1, s * 0.028);
        g.setFill(RING);
        g.fillOval(cx - ring, cy - ring, ring * 2, ring * 2);
        g.setFill(accent);
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
    }

    /** The tray lives in AWT, so the icon has to cross over. Copying the pixels by hand keeps the
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
