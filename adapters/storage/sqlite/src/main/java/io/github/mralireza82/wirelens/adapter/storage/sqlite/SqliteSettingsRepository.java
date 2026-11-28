package io.github.mralireza82.wirelens.adapter.storage.sqlite;

import io.github.mralireza82.wirelens.inspection.application.render.Radix;
import io.github.mralireza82.wirelens.inspection.application.render.RenderSettings;
import io.github.mralireza82.wirelens.inspection.application.render.TextEncoding;
import io.github.mralireza82.wirelens.presentation.traffic.settings.SettingsRepository;
import io.github.mralireza82.wirelens.presentation.traffic.settings.UserSettings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores user settings in an SQLite database as key/value rows. Any unreadable value falls back to its default, so
 * a damaged or older database never stops the app; a database that cannot be opened at all is logged and the
 * defaults are used.
 *
 * <p>The default location is {@code ~/.wirelens/wirelens.db}.
 */
public final class SqliteSettingsRepository implements SettingsRepository {
    private static final System.Logger LOG = System.getLogger(SqliteSettingsRepository.class.getName());
    private static final int SCHEMA_VERSION = 1;

    private final String url;

    public SqliteSettingsRepository(Path database) {
        try {
            Path parent = database.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the folder for " + database, e);
        }
        this.url = "jdbc:sqlite:" + database.toAbsolutePath();
    }

    /** The per-user database: {@code ~/.wirelens/wirelens.db}. */
    public static SqliteSettingsRepository forCurrentUser() {
        return new SqliteSettingsRepository(Path.of(System.getProperty("user.home"), ".wirelens", "wirelens.db"));
    }

    @Override
    public UserSettings load() {
        UserSettings d = UserSettings.DEFAULTS;
        Map<String, String> values;
        try {
            values = readAll();
        } catch (SQLException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot read settings from " + url + "; using defaults", e);
            return d;
        }
        try {
            return new UserSettings(
                    values.getOrDefault("listen.address", d.listenAddress()),
                    values.getOrDefault("listen.port", d.listenPort()),
                    values.getOrDefault("destination.address", d.destinationAddress()),
                    values.getOrDefault("destination.port", d.destinationPort()),
                    view(values, "view.source.", d.sourceView()),
                    view(values, "view.destination.", d.destinationView()),
                    bool(values.get("theme.dark"), d.darkTheme()),
                    integer(values.get("history.limit"), d.historyLimit()));
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Ignoring unreadable settings", e);
            return d;
        }
    }

    @Override
    public void save(UserSettings settings) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("listen.address", settings.listenAddress());
        values.put("listen.port", settings.listenPort());
        values.put("destination.address", settings.destinationAddress());
        values.put("destination.port", settings.destinationPort());
        putView(values, "view.source.", settings.sourceView());
        putView(values, "view.destination.", settings.destinationView());
        values.put("theme.dark", Boolean.toString(settings.darkTheme()));
        values.put("history.limit", Integer.toString(settings.historyLimit()));
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement upsert = connection.prepareStatement(
                    "INSERT INTO settings(key, value) VALUES (?, ?) "
                            + "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    upsert.setString(1, entry.getKey());
                    upsert.setString(2, entry.getValue());
                    upsert.addBatch();
                }
                upsert.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not save settings to " + url, e);
        }
    }

    /** Raw access for tests and diagnostics: writes one value as-is. */
    void put(String key, String value) throws SQLException {
        try (Connection connection = open();
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO settings(key, value) VALUES (?, ?) "
                             + "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            upsert.setString(1, key);
            upsert.setString(2, value);
            upsert.executeUpdate();
        }
    }

    private Map<String, String> readAll() throws SQLException {
        Map<String, String> values = new HashMap<>();
        try (Connection connection = open();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT key, value FROM settings")) {
            while (rows.next()) {
                values.put(rows.getString(1), rows.getString(2));
            }
        }
        return values;
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.executeUpdate("PRAGMA user_version = " + SCHEMA_VERSION);
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    private static RenderSettings view(Map<String, String> values, String prefix, RenderSettings fallback) {
        try {
            TextEncoding encoding = TextEncoding.valueOf(
                    values.getOrDefault(prefix + "encoding", fallback.encoding().name()));
            return new RenderSettings(encoding.isAvailable() ? encoding : fallback.encoding(),
                    Radix.valueOf(values.getOrDefault(prefix + "radix", fallback.radix().name())),
                    values.getOrDefault(prefix + "separator", fallback.separator()),
                    integer(values.get(prefix + "bytesPerLine"), fallback.bytesPerLine()));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static void putView(Map<String, String> values, String prefix, RenderSettings view) {
        values.put(prefix + "encoding", view.encoding().name());
        values.put(prefix + "radix", view.radix().name());
        values.put(prefix + "separator", view.separator());
        values.put(prefix + "bytesPerLine", Integer.toString(view.bytesPerLine()));
    }

    private static boolean bool(String value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int integer(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
