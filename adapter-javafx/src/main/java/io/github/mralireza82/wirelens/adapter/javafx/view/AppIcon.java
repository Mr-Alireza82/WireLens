package io.github.mralireza82.wirelens.adapter.javafx.view;

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

import java.util.List;

/** Draws the window icon (a lens over two opposite arrows) at the sizes operating systems ask for. */
public final class AppIcon {
    private AppIcon() {
    }

    public static List<Image> images() {
        return List.of(draw(16), draw(32), draw(64), draw(128), draw(256));
    }

    public static Image draw(int size) {
        Canvas canvas = new Canvas(size, size);
        GraphicsContext g = canvas.getGraphicsContext2D();
        double s = size;
        g.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#2f81f7")), new Stop(1, Color.web("#a371f7"))));
        g.fillRoundRect(0, 0, s, s, s * 0.42, s * 0.42);

        g.setStroke(Color.WHITE);
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineWidth(Math.max(1.5, s * 0.075));
        g.strokeArc(s * 0.2, s * 0.2, s * 0.6, s * 0.6, 0, 360, ArcType.OPEN);

        g.setLineWidth(Math.max(1.2, s * 0.06));
        arrow(g, s * 0.34, s * 0.43, s * 0.66, s * 0.43, s);
        arrow(g, s * 0.66, s * 0.57, s * 0.34, s * 0.57, s);

        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        return canvas.snapshot(parameters, null);
    }

    private static void arrow(GraphicsContext g, double x1, double y, double x2, double y2, double s) {
        g.strokeLine(x1, y, x2, y2);
        double head = s * 0.07 * Math.signum(x2 - x1);
        g.strokeLine(x2, y2, x2 - head, y2 - Math.abs(head));
        g.strokeLine(x2, y2, x2 - head, y2 + Math.abs(head));
    }
}
