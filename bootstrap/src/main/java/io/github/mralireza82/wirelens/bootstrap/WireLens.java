package io.github.mralireza82.wirelens.bootstrap;

import io.github.mralireza82.wirelens.adapter.javafx.AppDependencies;
import io.github.mralireza82.wirelens.adapter.javafx.WireLensApplication;
import io.github.mralireza82.wirelens.adapter.storage.JsonLinesCapture;
import io.github.mralireza82.wirelens.adapter.storage.PreferencesSettingsRepository;
import io.github.mralireza82.wirelens.adapter.storage.RawPayloadWriter;
import io.github.mralireza82.wirelens.adapter.storage.TextReportExporter;
import io.github.mralireza82.wirelens.adapter.tcp.TcpRelayEngine;
import io.github.mralireza82.wirelens.application.event.CompositeEventSink;
import io.github.mralireza82.wirelens.application.port.out.SettingsRepository;
import io.github.mralireza82.wirelens.application.proxy.ProxyService;
import io.github.mralireza82.wirelens.application.render.TextEncoding;
import io.github.mralireza82.wirelens.application.statistics.TrafficStatistics;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;

/**
 * Starts WireLens. All object creation happens here (manual dependency injection): the core never instantiates an
 * adapter, so swapping the TCP engine, the storage or even the UI only touches this class.
 *
 * <p>Options: {@code --demo} starts a built-in payment-host simulator and a few clients so there is traffic to look
 * at immediately; {@code --stress} adds four clients that send as fast as possible (load test).
 */
public final class WireLens {
    private WireLens() {
    }

    public static void main(String[] args) {
        Logging.configure();
        boolean stress = Arrays.asList(args).contains("--stress");
        boolean demo = stress || Arrays.asList(args).contains("--demo");

        Clock clock = Clock.systemDefaultZone();
        TrafficStatistics statistics = new TrafficStatistics(clock);
        CompositeEventSink events = new CompositeEventSink(statistics);
        ProxyService proxy = new ProxyService(new TcpRelayEngine(clock), events, clock);
        JsonLinesCapture capture = new JsonLinesCapture();

        DemoTraffic demoTraffic = demo ? DemoTraffic.prepare() : null;
        SettingsRepository settings = demo ? demoTraffic.settings() : PreferencesSettingsRepository.forCurrentUser();

        AppDependencies dependencies = new AppDependencies(proxy, proxy, statistics, events,
                List.of(capture, new TextReportExporter(TextEncoding.ASCII)), capture, new RawPayloadWriter(),
                settings, clock);
        if (demoTraffic != null) {
            WireLensApplication.whenReady(() -> demoTraffic.runAfterStartup(proxy, stress));
        }
        try {
            WireLensApplication.launch(dependencies, args);
        } finally {
            if (demoTraffic != null) {
                demoTraffic.close();
            }
            proxy.stop();
        }
    }
}
