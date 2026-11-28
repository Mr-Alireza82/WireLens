package io.github.mralireza82.wirelens.presentation.traffic.settings;

import io.github.mralireza82.wirelens.presentation.traffic.settings.UserSettings;

/** Remembers the user's choices between runs. Must never throw: fall back to defaults instead. */
public interface SettingsRepository {
    UserSettings load();

    void save(UserSettings settings);
}
