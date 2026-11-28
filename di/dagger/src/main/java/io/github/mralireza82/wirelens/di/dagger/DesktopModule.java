package io.github.mralireza82.wirelens.di.dagger;

import dagger.Module;
import dagger.Provides;
import io.github.mralireza82.wirelens.adapter.connection.tcp.TcpOneShotSender;
import io.github.mralireza82.wirelens.adapter.connection.tcp.TcpRelayEngine;
import io.github.mralireza82.wirelens.adapter.gui.javafx.AppDependencies;
import io.github.mralireza82.wirelens.adapter.storage.file.JsonLinesCapture;
import io.github.mralireza82.wirelens.adapter.storage.file.RawPayloadWriter;
import io.github.mralireza82.wirelens.adapter.storage.file.TextReportExporter;
import io.github.mralireza82.wirelens.adapter.storage.sqlite.SqliteSettingsRepository;
import io.github.mralireza82.wirelens.inspection.application.port.out.CaptureExporter;
import io.github.mralireza82.wirelens.inspection.application.port.out.CaptureImporter;
import io.github.mralireza82.wirelens.inspection.application.port.out.PayloadFileWriter;
import io.github.mralireza82.wirelens.inspection.application.render.TextEncoding;
import io.github.mralireza82.wirelens.inspection.application.statistics.TrafficStatistics;
import io.github.mralireza82.wirelens.presentation.traffic.settings.SettingsRepository;
import io.github.mralireza82.wirelens.relay.application.event.CompositeEventSink;
import io.github.mralireza82.wirelens.relay.application.intercept.HoldService;
import io.github.mralireza82.wirelens.relay.application.port.out.OneShotSender;
import io.github.mralireza82.wirelens.relay.application.port.out.RelayEngine;
import io.github.mralireza82.wirelens.relay.application.proxy.ProxyService;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import javax.inject.Singleton;

/**
 * The desktop choice of adapters: TCP sockets for the relay, SQLite for settings, file formats for captures, JavaFX
 * for the UI. The core classes carry no injection annotations; they are created here with plain constructors, so
 * contexts stay free of any DI framework and Android can wire the same classes with Hilt.
 */
@Module
public final class DesktopModule {
    private DesktopModule() {
    }

    @Provides
    @Singleton
    static Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Provides
    @Singleton
    static TrafficStatistics statistics(Clock clock) {
        return new TrafficStatistics(clock);
    }

    @Provides
    @Singleton
    static CompositeEventSink events(TrafficStatistics statistics) {
        return new CompositeEventSink(statistics);
    }

    @Provides
    @Singleton
    static OneShotSender oneShotSender() {
        return new TcpOneShotSender(Duration.ofSeconds(5));
    }

    @Provides
    @Singleton
    static HoldService hold(CompositeEventSink events, OneShotSender oneShot, Clock clock) {
        return new HoldService(events, oneShot, clock);
    }

    @Provides
    @Singleton
    static RelayEngine relayEngine(Clock clock, HoldService hold) {
        return new TcpRelayEngine(clock, hold);
    }

    @Provides
    @Singleton
    static ProxyService proxy(RelayEngine engine, CompositeEventSink events, Clock clock) {
        return new ProxyService(engine, events, clock);
    }

    @Provides
    @Singleton
    static JsonLinesCapture jsonLines() {
        return new JsonLinesCapture();
    }

    @Provides
    static List<CaptureExporter> exporters(JsonLinesCapture jsonLines) {
        return List.of(jsonLines, new TextReportExporter(TextEncoding.ASCII));
    }

    @Provides
    static CaptureImporter importer(JsonLinesCapture jsonLines) {
        return jsonLines;
    }

    @Provides
    static PayloadFileWriter payloadWriter() {
        return new RawPayloadWriter();
    }

    @Provides
    @Singleton
    static SettingsRepository settings(LaunchOptions options) {
        return options.settingsOverride().orElseGet(SqliteSettingsRepository::forCurrentUser);
    }

    @Provides
    @Singleton
    static AppDependencies ui(ProxyService proxy, HoldService hold, TrafficStatistics statistics,
                              CompositeEventSink events, List<CaptureExporter> exporters, CaptureImporter importer,
                              PayloadFileWriter payloadWriter, SettingsRepository settings, Clock clock) {
        return new AppDependencies(proxy, proxy, hold, statistics, events, exporters, importer, payloadWriter,
                settings, clock);
    }
}
