package io.github.mralireza82.wirelens.di.dagger;

import io.github.mralireza82.wirelens.presentation.traffic.settings.SettingsRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * What the platform's Main decides at start-up and hands to the component.
 *
 * @param settingsOverride settings to use instead of the SQLite database (demo mode keeps nothing on disk)
 */
public record LaunchOptions(Optional<SettingsRepository> settingsOverride) {
    public LaunchOptions {
        Objects.requireNonNull(settingsOverride, "settingsOverride");
    }

    public static LaunchOptions defaults() {
        return new LaunchOptions(Optional.empty());
    }
}
