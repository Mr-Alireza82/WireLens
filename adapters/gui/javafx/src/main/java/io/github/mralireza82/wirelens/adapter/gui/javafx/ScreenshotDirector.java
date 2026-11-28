package io.github.mralireza82.wirelens.adapter.gui.javafx;

import io.github.mralireza82.wirelens.adapter.gui.javafx.viewmodel.MainViewModel;
import io.github.mralireza82.wirelens.adapter.gui.javafx.viewmodel.TrafficEntry;
import io.github.mralireza82.wirelens.adapter.gui.javafx.viewmodel.TrafficPanelViewModel;
import io.github.mralireza82.wirelens.relay.domain.traffic.TrafficEvent;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Development aid that produces the README screenshots: with {@code -Dwirelens.screenshots=<folder>} (and usually
 * {@code --demo}) it waits for traffic, selects an entry in each panel, saves dark and light PNGs and exits.
 * Inactive unless the property is set.
 */
final class ScreenshotDirector {
    private final Path folder;
    private final Scene scene;
    private final MainViewModel model;
    private final List<Runnable> steps = new ArrayList<>();

    private ScreenshotDirector(Path folder, Scene scene, MainViewModel model) {
        this.folder = folder;
        this.scene = scene;
        this.model = model;
    }

    static void startIfRequested(Scene scene, MainViewModel model) {
        if (Boolean.getBoolean("wirelens.frameMonitor")) {
            startFrameMonitor(model);
        }
        String target = System.getProperty("wirelens.screenshots");
        if (target != null && !target.isBlank()) {
            new ScreenshotDirector(Path.of(target), scene, model).run();
        }
    }

    /** Prints the longest gap between two UI frames every two seconds: a direct measure of UI responsiveness. */
    private static void startFrameMonitor(MainViewModel model) {
        new javafx.animation.AnimationTimer() {
            private long last;
            private long worst;
            private long windowStart;

            @Override
            public void handle(long now) {
                if (last != 0) {
                    worst = Math.max(worst, now - last);
                }
                last = now;
                if (windowStart == 0) {
                    windowStart = now;
                }
                if (now - windowStart >= 2_000_000_000L) {
                    System.err.printf("frame-monitor worstGapMs=%d entries=%d/%d received=%d events=%d%n",
                            worst / 1_000_000, model.source().entries().size(), model.destination().entries().size(),
                            model.statistics().get().totalBytesReceived(), model.statistics().get().dataEvents());
                    worst = 0;
                    windowStart = now;
                }
            }
        }.start();
    }

    private void run() {
        steps.add(() -> {
            select(model.source(), 0);
            select(model.destination(), 1);
        });
        steps.add(() -> {
            model.source().composerMode().set(io.github.mralireza82.wirelens.adapter.gui.javafx.viewmodel.ComposerMode.TEXT);
            model.source().composerInput().set("HELLO FROM WIRELENS\\r\\n");
            model.source().send();
        });
        steps.add(() -> select(model.source(), 0));
        steps.add(() -> save("wirelens-dark.png"));
        steps.add(() -> model.source().holding().set(true));
        steps.add(() -> { });
        steps.add(() -> { });
        steps.add(() -> save("wirelens-hold.png"));
        steps.add(() -> {
            for (Node node : scene.getRoot().lookupAll(".connection-picker")) {
                ((javafx.scene.control.Button) node).fire();
                break;
            }
        });
        steps.add(() -> savePopup("wirelens-picker.png"));
        steps.add(() -> model.source().holding().set(false));
        steps.add(() -> {
            model.destination().settings().set(model.destination().settings().get()
                    .withRadix(io.github.mralireza82.wirelens.inspection.application.render.Radix.BINARY).withBytesPerLine(8));
            model.source().settings().set(model.source().settings().get().withSeparator("-"));
        });
        steps.add(() -> save("wirelens-binary.png"));
        steps.add(() -> {
            model.destination().settings().set(model.destination().settings().get()
                    .withRadix(io.github.mralireza82.wirelens.inspection.application.render.Radix.HEXADECIMAL).withBytesPerLine(16));
            model.source().settings().set(model.source().settings().get().withSeparator(" "));
            model.darkTheme().set(false);
        });
        steps.add(() -> save("wirelens-light.png"));
        steps.add(Platform::exit);
        double delay = Double.parseDouble(System.getProperty("wirelens.screenshots.delay", "12"));
        schedule(0, delay);
    }

    private void schedule(int index, double seconds) {
        if (index >= steps.size()) {
            return;
        }
        PauseTransition pause = new PauseTransition(Duration.seconds(seconds));
        pause.setOnFinished(event -> {
            steps.get(index).run();
            schedule(index + 1, 1.5);
        });
        pause.play();
    }

    /** Selects the newest data entry (skipping {@code skip} newer ones) and turns "follow" off so it stays put. */
    @SuppressWarnings("unchecked")
    private void select(TrafficPanelViewModel panel, int skip) {
        panel.followLatest().set(false);
        List<TrafficEntry> entries = panel.displayedEntries();
        int seen = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).event() instanceof TrafficEvent.DataTransferred data && data.payload().size() > 20
                    && model.openConnections().contains(data.link().id()) && seen++ == skip) {
                for (Node node : scene.getRoot().lookupAll(".traffic-list")) {
                    ListView<TrafficEntry> list = (ListView<TrafficEntry>) node;
                    if (list.getItems() == entries) {
                        list.getSelectionModel().clearAndSelect(i);
                        list.scrollTo(Math.max(0, i - 3));
                    }
                }
                return;
            }
        }
    }

    /** Saves the window with the open popover drawn on top of it at its on-screen position. */
    private void savePopup(String name) {
        WritableImage base = scene.snapshot(null);
        for (javafx.stage.Window window : javafx.stage.Window.getWindows()) {
            if (window instanceof javafx.stage.PopupWindow popup && popup.isShowing()) {
                javafx.scene.SnapshotParameters clear = new javafx.scene.SnapshotParameters();
                clear.setFill(javafx.scene.paint.Color.TRANSPARENT);
                WritableImage over = popup.getScene().getRoot().snapshot(clear, null);
                int dx = (int) (popup.getX() - scene.getWindow().getX() - scene.getX());
                int dy = (int) (popup.getY() - scene.getWindow().getY() - scene.getY());
                javafx.scene.image.PixelWriter writer = base.getPixelWriter();
                PixelReader reader = over.getPixelReader();
                for (int y = 0; y < (int) over.getHeight(); y++) {
                    for (int x = 0; x < (int) over.getWidth(); x++) {
                        int tx = x + dx;
                        int ty = y + dy;
                        int argb = reader.getArgb(x, y);
                        if ((argb >>> 24) > 0 && tx >= 0 && ty >= 0 && tx < base.getWidth() && ty < base.getHeight()) {
                            writer.setArgb(tx, ty, argb);
                        }
                    }
                }
                popup.hide();
            }
        }
        write(base, name);
    }

    private void save(String name) {
        write(scene.snapshot(null), name);
    }

    private void write(WritableImage image, String name) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffered.setRGB(x, y, pixels.getArgb(x, y));
            }
        }
        try {
            Files.createDirectories(folder);
            ImageIO.write(buffered, "png", folder.resolve(name).toFile());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot save " + name, e);
        }
    }
}
