package io.github.mralireza82.wirelens.adapter.storage.sqlite;

import io.github.mralireza82.wirelens.inspection.application.render.Radix;
import io.github.mralireza82.wirelens.inspection.application.render.RenderSettings;
import io.github.mralireza82.wirelens.inspection.application.render.TextEncoding;
import io.github.mralireza82.wirelens.presentation.traffic.settings.UserSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteSettingsRepositoryTest {
    @TempDir
    Path folder;

    @Test
    void remembersSettings() throws Exception {
        SqliteSettingsRepository repository = new SqliteSettingsRepository(folder.resolve("nested/wirelens.db"));
        assertThat(repository.load()).isEqualTo(UserSettings.DEFAULTS);

        UserSettings changed = UserSettings.DEFAULTS.withListen("0.0.0.0", "7000").withDestination("::1", "8584")
                .withViews(new RenderSettings(TextEncoding.EBCDIC_037, Radix.BINARY, "-", 0), RenderSettings.DEFAULT)
                .withDarkTheme(false);
        repository.save(changed);
        assertThat(repository.load()).isEqualTo(changed);
        assertThat(new SqliteSettingsRepository(folder.resolve("nested/wirelens.db")).load())
                .as("survives a restart").isEqualTo(changed);

        repository.put("view.source.radix", "BASE64");
        assertThat(repository.load().sourceView()).isEqualTo(RenderSettings.DEFAULT);
        repository.put("history.limit", "lots");
        assertThat(repository.load().historyLimit()).isEqualTo(UserSettings.DEFAULTS.historyLimit());
    }
}
