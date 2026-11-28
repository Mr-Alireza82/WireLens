package io.github.mralireza82.wirelens.app.windows;

import io.github.mralireza82.wirelens.adapter.gui.javafx.WireLensApplication;
import io.github.mralireza82.wirelens.di.dagger.DesktopComponent;
import io.github.mralireza82.wirelens.di.dagger.LaunchOptions;
import io.github.mralireza82.wirelens.relay.application.proxy.ProxyService;

import java.util.Arrays;
import java.util.Optional;

/**
 * Starts WireLens on Windows. Object creation lives in {@code di/dagger} ({@link DesktopComponent}); this class only
 * reads the command line, builds the graph and hands it to the JavaFX UI.
 *
 * <p>Options: {@code --demo} starts a built-in payment-host simulator and a few clients so there is traffic to look
 * at immediately; {@code --stress} adds four clients that send as fast as possible (load test).
 *
 * <p>Run it with {@code .\gradlew.bat :apps:windows:run} (add {@code --args="--demo"} for the demo).
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Logging.configure();
        boolean stress = Arrays.asList(args).contains("--stress");
        boolean demo = stress || Arrays.asList(args).contains("--demo");

        DemoTraffic demoTraffic = demo ? DemoTraffic.prepare() : null;
        DesktopComponent app = DesktopComponent.create(
                new LaunchOptions(demoTraffic == null ? Optional.empty() : Optional.of(demoTraffic.settings())));
        ProxyService proxy = app.proxy();
        if (demoTraffic != null) {
            WireLensApplication.whenReady(() -> demoTraffic.runAfterStartup(proxy, stress));
        }
        try {
            WireLensApplication.launch(app.ui(), args);
        } finally {
            if (demoTraffic != null) {
                demoTraffic.close();
            }
            proxy.stop();
        }
    }
}
