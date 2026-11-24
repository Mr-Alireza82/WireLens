package io.github.mralireza82.wirelens.application.port.out;

import io.github.mralireza82.wirelens.application.settings.UserSettings;

/** Remembers the user's choices between runs. Must never throw: fall back to defaults instead. */
public interface SettingsRepository {
    UserSettings load();

    void save(UserSettings settings);
}
