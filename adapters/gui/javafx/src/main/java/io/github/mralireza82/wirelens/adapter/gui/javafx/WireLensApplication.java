package io.github.mralireza82.wirelens.adapter.gui.javafx;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import io.github.mralireza82.wirelens.adapter.gui.javafx.util.MonoFont;
import io.github.mralireza82.wirelens.adapter.gui.javafx.view.AppIcon;
import io.github.mralireza82.wirelens.adapter.gui.javafx.view.MainView;
import io.github.mralireza82.wirelens.adapter.gui.javafx.view.Toasts;
import io.github.mralireza82.wirelens.adapter.gui.javafx.viewmodel.MainViewModel;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The JavaFX entry point. JavaFX creates this class itself, so the composition root hands over the dependencies
 * through {@link #launch(AppDependencies, String...)} before the toolkit starts.
 */
public final class WireLensApplication extends Application {
    private static final int UI_QUEUE_CAPACITY = 200_000;
    private static final long UI_QUEUE_BYTES = 48L * 1024 * 1024;
    private static volatile AppDependencies dependencies;
    private static final java.util.concurrent.CompletableFuture<Void> READY = new java.util.concurrent.CompletableFuture<>();

    private final ExecutorService background = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "wirelens-ui-tasks");
        thread.setDaemon(true);
        return thread;
    });
    private FxEventBridge bridge;
    private MainViewModel model;
    private Timeline ticker;

    /** Starts the UI; blocks until the window is closed. */
    public static void launch(AppDependencies appDependencies, String... args) {
        dependencies = Objects.requireNonNull(appDependencies, "appDependencies");
        Application.launch(WireLensApplication.class, args);
    }

    /** Runs {@code action} (on the FX thread) once the window is shown and receiving events. */
    public static void whenReady(Runnable action) {
        READY.thenRun(action);
    }

    @Override
    public void start(Stage stage) {
        AppDependencies deps = Objects.requireNonNull(dependencies, "call WireLensApplication.launch(...)");
        bridge = new FxEventBridge(UI_QUEUE_CAPACITY, UI_QUEUE_BYTES, deps.clock());
        deps.events().add(bridge);

        StackPane root = new StackPane();
        Toasts toasts = new Toasts(root);
        model = new MainViewModel(deps, background, Platform::runLater, toasts);
        bridge.subscribe(model::accept);
        applyTheme(model.darkTheme().get());
        model.darkTheme().addListener((obs, old, dark) -> applyTheme(dark));

        MainView view = new MainView(model, () -> stage);
        root.getChildren().add(0, view);

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = Double.parseDouble(System.getProperty("wirelens.width", "" + Math.min(1440, screen.getWidth() * 0.9)));
        double height = Double.parseDouble(System.getProperty("wirelens.height", "" + Math.min(900, screen.getHeight() * 0.9)));
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().addAll(
                Objects.requireNonNull(WireLensApplication.class.getResource("wirelens.css")).toExternalForm(),
                MonoFont.stylesheet());
        installShortcuts(scene, view);

        stage.setTitle("WireLens");
        stage.getIcons().setAll(AppIcon.images());
        stage.setMinWidth(760);
        stage.setMinHeight(520);
        stage.setScene(scene);
        stage.show();

        ticker = new Timeline(new KeyFrame(Duration.millis(500), event -> model.tick()));
        ticker.setCycleCount(Timeline.INDEFINITE);
        ticker.play();
        bridge.start();
        ScreenshotDirector.startIfRequested(scene, model);
        READY.complete(null);
    }

    private void installShortcuts(Scene scene, MainView view) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), model::toggleRunning);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN), model::clearAll);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN),
                view::openCapture);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN),
                view::exportAll);
    }

    private static void applyTheme(boolean dark) {
        Application.setUserAgentStylesheet(dark ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
    }

    @Override
    public void stop() throws InterruptedException {
        if (ticker != null) {
            ticker.stop();
        }
        if (bridge != null) {
            bridge.stop();
            dependencies.events().remove(bridge);
        }
        if (model != null) {
            model.saveSettings();
        }
        dependencies.proxy().stop();
        background.shutdown();
        background.awaitTermination(2, TimeUnit.SECONDS);
    }
}
