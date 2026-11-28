package io.github.mralireza82.wirelens.adapter.gui.javafx.viewmodel;

import io.github.mralireza82.wirelens.adapter.gui.javafx.AppDependencies;
import io.github.mralireza82.wirelens.relay.application.event.CompositeEventSink;
import io.github.mralireza82.wirelens.inspection.application.port.out.CaptureExporter;
import io.github.mralireza82.wirelens.inspection.application.port.out.CaptureImporter;
import io.github.mralireza82.wirelens.relay.application.port.out.RelayEngine;
import io.github.mralireza82.wirelens.relay.application.port.out.RunningRelay;
import io.github.mralireza82.wirelens.presentation.traffic.settings.SettingsRepository;
import io.github.mralireza82.wirelens.relay.application.proxy.ProxyService;
import io.github.mralireza82.wirelens.relay.application.proxy.ProxyState;
import io.github.mralireza82.wirelens.presentation.traffic.settings.UserSettings;
import io.github.mralireza82.wirelens.inspection.application.statistics.TrafficStatistics;
import io.github.mralireza82.wirelens.kernel.net.Endpoint;
import io.github.mralireza82.wirelens.kernel.id.ConnectionId;
import io.github.mralireza82.wirelens.relay.domain.traffic.Link;
import io.github.mralireza82.wirelens.kernel.bytes.Payload;
import io.github.mralireza82.wirelens.relay.domain.traffic.Side;
import io.github.mralireza82.wirelens.relay.domain.traffic.TrafficEvent;
import io.github.mralireza82.wirelens.relay.application.intercept.HoldService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MainViewModelTest {
    private final List<Endpoint> started = new ArrayList<>();
    private final List<List<? extends TrafficEvent>> exported = new ArrayList<>();
    private UserSettings saved = UserSettings.DEFAULTS;

    private final RelayEngine engine = (configuration, sink) -> {
        started.add(configuration.listen());
        started.add(configuration.destination());
        return new RunningRelay() {
            @Override
            public Endpoint listeningOn() {
                return configuration.listen();
            }

            @Override
            public void changeDestination(Endpoint destination) {
                started.add(destination);
            }

            @Override
            public void send(ConnectionId connection, Side side, Payload payload) {
            }

            @Override
            public void disconnect(ConnectionId connection) {
            }

            @Override
            public Set<ConnectionId> openConnections() {
                return Set.of();
            }

            @Override
            public void close() {
            }
        };
    };

    private final CompositeEventSink events = new CompositeEventSink();
    private final TrafficStatistics statistics = new TrafficStatistics(Clock.systemUTC());
    private final ProxyService proxy = new ProxyService(engine, events, Clock.systemUTC());
    private final CaptureExporter exporter = new CaptureExporter() {
        @Override
        public void export(List<? extends TrafficEvent> list, Path target) {
            exported.add(list);
        }

        @Override
        public String displayName() {
            return "test";
        }

        @Override
        public String fileExtension() {
            return "t";
        }
    };
    private final CaptureImporter importer = new CaptureImporter() {
        @Override
        public List<TrafficEvent> load(Path source) {
            return List.of(new TrafficEvent.Notice(Instant.now(), Side.DESTINATION, "imported"));
        }

        @Override
        public String displayName() {
            return "test";
        }

        @Override
        public String fileExtension() {
            return "t";
        }
    };
    private final SettingsRepository settings = new SettingsRepository() {
        @Override
        public UserSettings load() {
            return saved;
        }

        @Override
        public void save(UserSettings value) {
            saved = value;
        }
    };
    private final HoldService hold = new HoldService(event -> { }, (target, payload, timeout) -> Payload.empty(),
            Clock.systemUTC());
    private final MainViewModel model = new MainViewModel(new AppDependencies(proxy, proxy, hold, statistics, events,
            List.of(exporter), importer, (payload, target) -> { }, settings, Clock.systemUTC()),
            Runnable::run, Runnable::run, (message, error) -> { });

    private static Link link(long id, Side side) {
        return new Link(new ConnectionId(id), side, Endpoint.of("127.0.0.1", 9000), Endpoint.of("10.0.0.2", 5000));
    }

    @Test
    void startsOnlyWithValidEndpointsAndRemembersThem() {
        model.source().endpoint().address().set("999.1.1.1");
        assertThat(model.canStart().get()).isFalse();
        model.source().endpoint().address().set("0.0.0.0");
        model.destination().endpoint().address().set("::1");
        assertThat(model.canStart().get()).isTrue();

        model.start();

        assertThat(model.status().get().state()).isEqualTo(ProxyState.RUNNING);
        assertThat(model.running().get()).isTrue();
        assertThat(started).containsExactly(Endpoint.of("0.0.0.0", 9000), Endpoint.of("::1", 8583));
        assertThat(saved.listenAddress()).isEqualTo("0.0.0.0");
        assertThat(saved.destinationAddress()).isEqualTo("::1");

        model.toggleRunning();
        assertThat(model.status().get().state()).isEqualTo(ProxyState.STOPPED);
    }

    @Test
    void appliesANewDestinationOnlyWhenItDiffers() {
        model.start();
        assertThat(model.canApplyDestination().get()).isFalse();
        model.destination().endpoint().port().set("8584");
        assertThat(model.canApplyDestination().get()).isTrue();
        model.applyDestination();
        assertThat(started).last().isEqualTo(Endpoint.of("127.0.0.1", 8584));
        assertThat(model.canApplyDestination().get()).isFalse();
    }

    @Test
    void tracksOpenConnectionsFromEvents() {
        model.start();
        model.accept(List.of(new TrafficEvent.ConnectionOpened(Instant.now(), link(1, Side.SOURCE)),
                new TrafficEvent.ConnectionOpened(Instant.now(), link(2, Side.SOURCE))));
        model.accept(List.of(new TrafficEvent.ConnectionClosed(Instant.now(), link(1, Side.SOURCE), 0, 0,
                Duration.ZERO, "client closed")));
        assertThat(model.openConnections()).containsExactly(new ConnectionId(2));
        model.stop();
        assertThat(model.openConnections()).isEmpty();
    }

    @Test
    void exportsBothPanelsInTimeOrderAndImports() {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        model.accept(List.of(new TrafficEvent.Notice(t.plusSeconds(2), Side.SOURCE, "b"),
                new TrafficEvent.Notice(t.plusSeconds(1), Side.DESTINATION, "a")));
        model.exportAll(exporter, Path.of("x.t"));
        assertThat(exported.getFirst()).extracting(e -> ((TrafficEvent.Notice) e).message()).containsExactly("a", "b");

        model.openCapture(Path.of("x.t"));
        assertThat(model.destination().entries()).hasSize(2);
    }

    @Test
    void samplesThroughput() {
        model.tick();
        statistics.publish(new TrafficEvent.DataTransferred(Instant.now(), link(1, Side.SOURCE),
                io.github.mralireza82.wirelens.relay.domain.traffic.Direction.INBOUND, Payload.copyOf(new byte[100]),
                io.github.mralireza82.wirelens.relay.domain.traffic.DataOrigin.RELAYED));
        model.tick();
        assertThat(model.statistics().get().bytesFromClients()).isEqualTo(100);
        assertThat(model.upstreamHistory()).hasSize(2);
        model.resetStatistics();
        assertThat(model.statistics().get().bytesFromClients()).isZero();
    }
}
