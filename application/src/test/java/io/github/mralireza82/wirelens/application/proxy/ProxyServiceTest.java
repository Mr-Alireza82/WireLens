package io.github.mralireza82.wirelens.application.proxy;

import io.github.mralireza82.wirelens.application.port.out.RelayEngine;
import io.github.mralireza82.wirelens.application.port.out.RunningRelay;
import io.github.mralireza82.wirelens.application.port.out.UnknownConnectionException;
import io.github.mralireza82.wirelens.domain.net.Endpoint;
import io.github.mralireza82.wirelens.domain.traffic.ConnectionId;
import io.github.mralireza82.wirelens.domain.traffic.FailureStage;
import io.github.mralireza82.wirelens.domain.traffic.Payload;
import io.github.mralireza82.wirelens.domain.traffic.Side;
import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.BindException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProxyServiceTest {
    private final Endpoint listen = Endpoint.of("127.0.0.1", 9000);
    private final Endpoint destination = Endpoint.of("10.0.0.5", 8583);
    private final List<TrafficEvent> events = new CopyOnWriteArrayList<>();
    private final FakeRelay relay = new FakeRelay();
    private boolean failToBind;

    private final RelayEngine engine = (configuration, sink) -> {
        if (failToBind) {
            throw new BindException("Address already in use");
        }
        relay.destination = configuration.destination();
        return relay;
    };
    private final ProxyService service = new ProxyService(engine, events::add, Clock.systemUTC());

    @Test
    void startsAndStopsThroughTheStateMachine() {
        List<ProxyState> seen = new ArrayList<>();
        service.onStatusChanged(status -> seen.add(status.state()));

        assertThat(service.start(listen, destination).state()).isEqualTo(ProxyState.RUNNING);
        assertThat(service.status().listen()).contains(listen);
        assertThat(service.stop().state()).isEqualTo(ProxyState.STOPPED);

        assertThat(seen).containsExactly(ProxyState.STARTING, ProxyState.RUNNING, ProxyState.STOPPING,
                ProxyState.STOPPED);
        assertThat(relay.closed).isTrue();
        assertThat(events).extracting(TrafficEvent::kind).allMatch(kind -> kind.name().equals("NOTICE"));
    }

    @Test
    void ignoresASecondStart() {
        service.start(listen, destination);
        assertThat(service.start(listen, destination).state()).isEqualTo(ProxyState.RUNNING);
    }

    @Test
    void reportsBindFailuresAsStateAndEvent() {
        failToBind = true;
        ProxyStatus status = service.start(listen, destination);

        assertThat(status.state()).isEqualTo(ProxyState.FAILED);
        assertThat(status.error()).contains("Address already in use");
        assertThat(events).singleElement().isInstanceOfSatisfying(TrafficEvent.ConnectionFailed.class,
                failed -> assertThat(failed.stage()).isEqualTo(FailureStage.LISTEN));

        failToBind = false;
        assertThat(service.start(listen, destination).state()).isEqualTo(ProxyState.RUNNING);
    }

    @Test
    void changesTheDestinationOfARunningRelay() {
        service.start(listen, destination);
        Endpoint other = Endpoint.of("::1", 8584);
        service.changeDestination(other);

        assertThat(relay.destination).isEqualTo(other);
        assertThat(service.status().destination()).contains(other);
        assertThat(events).last().isInstanceOfSatisfying(TrafficEvent.Notice.class,
                notice -> assertThat(notice.message()).contains("[::1]:8584"));
    }

    @Test
    void remembersTheDestinationWhileStopped() {
        service.changeDestination(destination);
        assertThat(service.status().destination()).contains(destination);
        assertThat(events).isEmpty();
    }

    @Test
    void forwardsConnectionCommands() throws IOException {
        assertThatThrownBy(() -> service.send(new ConnectionId(1), Side.SOURCE, Payload.empty()))
                .isInstanceOf(UnknownConnectionException.class);
        service.start(listen, destination);
        service.send(new ConnectionId(1), Side.DESTINATION, Payload.copyOf(new byte[] {1}));
        service.disconnect(new ConnectionId(1));

        assertThat(relay.sent).containsExactly("#1 DESTINATION 1");
        assertThat(relay.disconnected).containsExactly(new ConnectionId(1));
        assertThat(service.openConnections()).isEmpty();
    }

    private static final class FakeRelay implements RunningRelay {
        Endpoint destination;
        boolean closed;
        final List<String> sent = new ArrayList<>();
        final List<ConnectionId> disconnected = new ArrayList<>();

        @Override
        public Endpoint listeningOn() {
            return Endpoint.of("127.0.0.1", 9000);
        }

        @Override
        public void changeDestination(Endpoint destination) {
            this.destination = destination;
        }

        @Override
        public void send(ConnectionId connection, Side side, Payload payload) {
            sent.add(connection + " " + side + " " + payload.size());
        }

        @Override
        public void disconnect(ConnectionId connection) {
            disconnected.add(connection);
        }

        @Override
        public Set<ConnectionId> openConnections() {
            return Set.of();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
