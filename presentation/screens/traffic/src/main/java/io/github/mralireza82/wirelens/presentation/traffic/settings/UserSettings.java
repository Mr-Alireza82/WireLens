package io.github.mralireza82.wirelens.presentation.traffic.settings;

import io.github.mralireza82.wirelens.inspection.application.render.RenderSettings;

import java.util.Objects;

/**
 * What the app remembers between runs. Addresses are kept as typed (not parsed) so a half-edited value never
 * prevents the app from starting.
 */
public record UserSettings(String listenAddress, String listenPort, String destinationAddress,
                           String destinationPort, RenderSettings sourceView, RenderSettings destinationView,
                           boolean darkTheme, int historyLimit) {
    public static final int MIN_HISTORY = 1_000;
    public static final int MAX_HISTORY = 500_000;

    public static final UserSettings DEFAULTS = new UserSettings("127.0.0.1", "9000", "127.0.0.1", "8583",
            RenderSettings.DEFAULT, RenderSettings.DEFAULT, true, 50_000);

    public UserSettings {
        Objects.requireNonNull(listenAddress, "listenAddress");
        Objects.requireNonNull(listenPort, "listenPort");
        Objects.requireNonNull(destinationAddress, "destinationAddress");
        Objects.requireNonNull(destinationPort, "destinationPort");
        Objects.requireNonNull(sourceView, "sourceView");
        Objects.requireNonNull(destinationView, "destinationView");
        historyLimit = Math.max(MIN_HISTORY, Math.min(MAX_HISTORY, historyLimit));
    }

    public UserSettings withListen(String address, String port) {
        return new UserSettings(address, port, destinationAddress, destinationPort, sourceView, destinationView,
                darkTheme, historyLimit);
    }

    public UserSettings withDestination(String address, String port) {
        return new UserSettings(listenAddress, listenPort, address, port, sourceView, destinationView, darkTheme,
                historyLimit);
    }

    public UserSettings withViews(RenderSettings source, RenderSettings destination) {
        return new UserSettings(listenAddress, listenPort, destinationAddress, destinationPort, source, destination,
                darkTheme, historyLimit);
    }

    public UserSettings withDarkTheme(boolean dark) {
        return new UserSettings(listenAddress, listenPort, destinationAddress, destinationPort, sourceView,
                destinationView, dark, historyLimit);
    }
}
