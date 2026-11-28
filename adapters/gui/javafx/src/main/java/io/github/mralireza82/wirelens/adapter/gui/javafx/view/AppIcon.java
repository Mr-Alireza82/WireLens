package io.github.mralireza82.wirelens.adapter.gui.javafx.view;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.List;

/**
 * The WireLens mark: a wire runs through a magnifying lens, and inside the lens the plain line turns into a
 * digital signal, i.e. the bytes become visible. Drawn in code so it is sharp at every size the OS asks for; the
 * same geometry is in {@code docs/images/logo.svg} (coordinates there are in a 0..100 box).
 */
public final class AppIcon {
    private static final Color BLUE = Color.web("#2f81f7");
    private static final Color PURPLE = Color.web("#a371f7");

    private AppIcon() {
    }

    public static List<Image> images() {
        return List.of(draw(16), draw(24), draw(32), draw(48), draw(64), draw(128), draw(256));
    }

    public static Image draw(int size) {
        Canvas canvas = new Canvas(size, size);
        GraphicsContext g = canvas.getGraphicsContext2D();
        double s = size / 100.0;
        g.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE, new Stop(0, BLUE), new Stop(1, PURPLE)));
        g.fillRoundRect(0, 0, size, size, 44 * s, 44 * s);

        g.setStroke(Color.WHITE);
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);

        // the wire, entering from the left and leaving to the right
        g.setLineWidth(Math.max(1, 5 * s));
        g.strokeLine(9 * s, 44 * s, 21 * s, 44 * s);
        g.strokeLine(67 * s, 44 * s, 91 * s, 44 * s);
        if (size >= 24) {
            // magnified: the same wire seen as bits
            double[] x = {21, 29, 29, 37, 37, 44, 44, 52, 52, 59, 59, 67};
            double[] y = {44, 44, 35, 35, 53, 53, 35, 35, 53, 53, 44, 44};
            for (int i = 0; i < x.length; i++) {
                x[i] *= s;
                y[i] *= s;
            }
            g.setLineWidth(Math.max(1, 4.5 * s));
            g.strokePolyline(x, y, x.length);
        } else {
            g.strokeLine(21 * s, 44 * s, 67 * s, 44 * s);
        }

        // the lens and its handle
        g.setLineWidth(Math.max(1.5, 7.5 * s));
        g.strokeArc(20 * s, 20 * s, 48 * s, 48 * s, 0, 360, ArcType.OPEN);
        g.setLineWidth(Math.max(2, 11 * s));
        g.strokeLine(62 * s, 62 * s, 80 * s, 80 * s);

        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        return canvas.snapshot(parameters, null);
    }
}
