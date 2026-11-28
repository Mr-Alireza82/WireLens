package io.github.mralireza82.wirelens.adapter.gui.javafx;

import io.github.mralireza82.wirelens.relay.application.event.CompositeEventSink;
import io.github.mralireza82.wirelens.relay.application.port.in.ConnectionCommands;
import io.github.mralireza82.wirelens.relay.application.port.in.HoldCommands;
import io.github.mralireza82.wirelens.relay.application.port.in.ProxyControl;
import io.github.mralireza82.wirelens.inspection.application.port.out.CaptureExporter;
import io.github.mralireza82.wirelens.inspection.application.port.out.CaptureImporter;
import io.github.mralireza82.wirelens.inspection.application.port.out.PayloadFileWriter;
import io.github.mralireza82.wirelens.presentation.traffic.settings.SettingsRepository;
import io.github.mralireza82.wirelens.inspection.application.statistics.TrafficStatistics;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Everything the UI needs from the rest of the application, handed over by the composition root. The UI depends
 * only on ports and application services, never on a concrete adapter.
 *
 * @param hold   hold mode (the relay consults the same service before forwarding)
 * @param events where the UI subscribes to traffic events (the relay publishes into the same fan-out)
 */
public record AppDependencies(ProxyControl proxy, ConnectionCommands connections, HoldCommands hold,
                              TrafficStatistics statistics,
                              CompositeEventSink events, List<CaptureExporter> exporters, CaptureImporter importer,
                              PayloadFileWriter payloadWriter, SettingsRepository settings, Clock clock) {
    public AppDependencies {
        Objects.requireNonNull(proxy, "proxy");
        Objects.requireNonNull(connections, "connections");
        Objects.requireNonNull(hold, "hold");
        Objects.requireNonNull(statistics, "statistics");
        Objects.requireNonNull(events, "events");
        exporters = List.copyOf(exporters);
        Objects.requireNonNull(importer, "importer");
        Objects.requireNonNull(payloadWriter, "payloadWriter");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(clock, "clock");
    }
}
