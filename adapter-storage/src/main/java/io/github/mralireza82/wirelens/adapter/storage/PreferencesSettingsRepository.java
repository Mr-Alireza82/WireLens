package io.github.mralireza82.wirelens.adapter.storage;

import io.github.mralireza82.wirelens.application.port.out.SettingsRepository;
import io.github.mralireza82.wirelens.application.render.Radix;
import io.github.mralireza82.wirelens.application.render.RenderSettings;
import io.github.mralireza82.wirelens.application.render.TextEncoding;
import io.github.mralireza82.wirelens.application.settings.UserSettings;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Stores user settings with {@link Preferences}: the registry on Windows, a plist on macOS, an XML file on Linux.
 * Any unreadable value falls back to its default, so a corrupt store never stops the app.
 */
public final class PreferencesSettingsRepository implements SettingsRepository {
    private static final System.Logger LOG = System.getLogger(PreferencesSettingsRepository.class.getName());

    private final Preferences node;

    public PreferencesSettingsRepository(Preferences node) {
        this.node = node;
    }

    public static PreferencesSettingsRepository forCurrentUser() {
        return new PreferencesSettingsRepository(Preferences.userRoot().node("io/github/mralireza82/wirelens"));
    }

    @Override
    public UserSettings load() {
        UserSettings d = UserSettings.DEFAULTS;
        try {
            return new UserSettings(
                    node.get("listen.address", d.listenAddress()),
                    node.get("listen.port", d.listenPort()),
                    node.get("destination.address", d.destinationAddress()),
                    node.get("destination.port", d.destinationPort()),
                    view("view.source.", d.sourceView()),
                    view("view.destination.", d.destinationView()),
                    node.getBoolean("theme.dark", d.darkTheme()),
                    node.getInt("history.limit", d.historyLimit()));
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Ignoring unreadable settings", e);
            return d;
        }
    }

    @Override
    public void save(UserSettings settings) {
        try {
            node.put("listen.address", settings.listenAddress());
            node.put("listen.port", settings.listenPort());
            node.put("destination.address", settings.destinationAddress());
            node.put("destination.port", settings.destinationPort());
            putView("view.source.", settings.sourceView());
            putView("view.destination.", settings.destinationView());
            node.putBoolean("theme.dark", settings.darkTheme());
            node.putInt("history.limit", settings.historyLimit());
            node.flush();
        } catch (BackingStoreException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not save settings", e);
        }
    }

    private RenderSettings view(String prefix, RenderSettings fallback) {
        try {
            TextEncoding encoding = TextEncoding.valueOf(node.get(prefix + "encoding", fallback.encoding().name()));
            return new RenderSettings(encoding.isAvailable() ? encoding : fallback.encoding(),
                    Radix.valueOf(node.get(prefix + "radix", fallback.radix().name())),
                    node.get(prefix + "separator", fallback.separator()),
                    node.getInt(prefix + "bytesPerLine", fallback.bytesPerLine()));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private void putView(String prefix, RenderSettings view) {
        node.put(prefix + "encoding", view.encoding().name());
        node.put(prefix + "radix", view.radix().name());
        node.put(prefix + "separator", view.separator());
        node.putInt(prefix + "bytesPerLine", view.bytesPerLine());
    }
}
