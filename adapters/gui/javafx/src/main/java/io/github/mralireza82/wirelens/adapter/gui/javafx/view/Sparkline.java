package io.github.mralireza82.wirelens.adapter.gui.javafx.view;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/** A small, resizable two-series throughput chart drawn on a canvas (cheaper than a full JavaFX chart). */
final class Sparkline extends Region {
    private static final double MIN_SCALE = 1024; // keep idle noise flat: at least 1 KB/s full scale
    private final Canvas canvas = new Canvas();
    private final ObservableList<Double> upstream;
    private final ObservableList<Double> downstream;
    private final int capacity;
    private final Color upColor = Color.web("#2f81f7");
    private final Color downColor = Color.web("#a371f7");

    Sparkline(ObservableList<Double> upstream, ObservableList<Double> downstream, int capacity) {
        this.upstream = upstream;
        this.downstream = downstream;
        this.capacity = capacity;
        getChildren().add(canvas);
        setMinSize(60, 24);
        setPrefSize(220, 40);
        ListChangeListener<Double> redraw = change -> draw();
        upstream.addListener(redraw);
        downstream.addListener(redraw);
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(snapSizeX(getWidth()));
        canvas.setHeight(snapSizeY(getHeight()));
        draw();
    }

    private void draw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, width, height);
        if (width <= 0 || height <= 0) {
            return;
        }
        double max = MIN_SCALE;
        for (double value : upstream) {
            max = Math.max(max, value);
        }
        for (double value : downstream) {
            max = Math.max(max, value);
        }
        series(g, downstream, max, width, height, downColor);
        series(g, upstream, max, width, height, upColor);
    }

    private void series(GraphicsContext g, ObservableList<Double> values, double max, double width, double height,
                        Color color) {
        int count = values.size();
        if (count < 2) {
            return;
        }
        double step = width / (capacity - 1);
        double startX = width - step * (count - 1);
        double[] xs = new double[count + 2];
        double[] ys = new double[count + 2];
        for (int i = 0; i < count; i++) {
            xs[i] = startX + i * step;
            ys[i] = height - 2 - (height - 4) * Math.min(1, values.get(i) / max);
        }
        xs[count] = xs[count - 1];
        ys[count] = height;
        xs[count + 1] = xs[0];
        ys[count + 1] = height;
        g.setFill(color.deriveColor(0, 1, 1, 0.18));
        g.fillPolygon(xs, ys, count + 2);
        g.setStroke(color);
        g.setLineWidth(1.5);
        g.strokePolyline(xs, ys, count);
    }
}
