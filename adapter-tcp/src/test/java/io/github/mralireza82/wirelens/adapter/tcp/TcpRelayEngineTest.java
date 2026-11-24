package io.github.mralireza82.wirelens.adapter.tcp;

import io.github.mralireza82.wirelens.application.port.out.RunningRelay;
import io.github.mralireza82.wirelens.application.port.out.UnknownConnectionException;
import io.github.mralireza82.wirelens.domain.net.Endpoint;
import io.github.mralireza82.wirelens.domain.net.IpAddress;
import io.github.mralireza82.wirelens.domain.net.Port;
import io.github.mralireza82.wirelens.domain.proxy.ProxyConfiguration;
import io.github.mralireza82.wirelens.domain.traffic.ConnectionId;
import io.github.mralireza82.wirelens.domain.traffic.DataOrigin;
import io.github.mralireza82.wirelens.domain.traffic.Direction;
import io.github.mralireza82.wirelens.domain.traffic.EventKind;
import io.github.mralireza82.wirelens.domain.traffic.FailureStage;
import io.github.mralireza82.wirelens.domain.traffic.Payload;
import io.github.mralireza82.wirelens.domain.traffic.Side;
import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

@Timeout(30)
class TcpRelayEngineTest {
    private static final Endpoint ANY_LOOPBACK_PORT = new Endpoint(IpAddress.loopbackIpv4(), new Port(0));

    private final List<TrafficEvent> events = new CopyOnWriteArrayList<>();
    private final TcpRelayEngine engine = new TcpRelayEngine(Clock.systemUTC());
    private final List<AutoCloseable> toClose = new ArrayList<>();

    @AfterEach
    void closeEverything() throws Exception {
        for (AutoCloseable closeable : toClose.reversed()) {
            closeable.close();
        }
    }

    private RunningRelay startRelay(Endpoint destination) throws IOException {
        RunningRelay relay = engine.start(ProxyConfiguration.of(ANY_LOOPBACK_PORT, destination), events::add);
        toClose.add(relay);
        return relay;
    }

    private Socket connect(RunningRelay relay) throws IOException {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), relay.listeningOn().port().value());
        toClose.add(socket);
        return socket;
    }

    @Test
    void relaysBothDirectionsAndReportsEveryStep() throws Exception {
        TestServers.TransformServer server = new TestServers.TransformServer("127.0.0.1",
                bytes -> ("PONG:" + new String(bytes, StandardCharsets.US_ASCII)).getBytes(StandardCharsets.US_ASCII));
        toClose.add(server);
        RunningRelay relay = startRelay(server.endpoint());

        Socket client = connect(relay);
        client.getOutputStream().write("PING".getBytes(StandardCharsets.US_ASCII));
        byte[] reply = client.getInputStream().readNBytes(9);
        client.shutdownOutput();
        assertThat(client.getInputStream().read()).isEqualTo(-1);
        client.close();

        assertThat(new String(reply, StandardCharsets.US_ASCII)).isEqualTo("PONG:PING");
        awaitEvent(e -> e instanceof TrafficEvent.ConnectionClosed c && c.side() == Side.SOURCE);
        awaitEvent(e -> e instanceof TrafficEvent.ConnectionClosed c && c.side() == Side.DESTINATION);

        assertThat(kinds(Side.SOURCE)).startsWith(EventKind.OPENED).endsWith(EventKind.CLOSED)
                .contains(EventKind.DATA).filteredOn(k -> k == EventKind.STREAM_ENDED).hasSize(2);
        assertThat(kinds(Side.DESTINATION)).startsWith(EventKind.OPENED).endsWith(EventKind.CLOSED);
        assertThat(data(Side.SOURCE, Direction.INBOUND)).isEqualTo("PING");
        assertThat(data(Side.DESTINATION, Direction.OUTBOUND)).isEqualTo("PING");
        assertThat(data(Side.DESTINATION, Direction.INBOUND)).isEqualTo("PONG:PING");
        assertThat(data(Side.SOURCE, Direction.OUTBOUND)).isEqualTo("PONG:PING");

        TrafficEvent.ConnectionClosed closed = (TrafficEvent.ConnectionClosed) awaitEvent(
                e -> e instanceof TrafficEvent.ConnectionClosed c && c.side() == Side.SOURCE);
        assertThat(closed.bytesIn()).isEqualTo(4);
        assertThat(closed.bytesOut()).isEqualTo(9);
        assertThat(closed.reason()).isEqualTo("client closed the connection");
        assertThat(closed.link().remote().port().value()).isEqualTo(client.getLocalPort());
        assertThat(closed.link().id()).isEqualTo(new ConnectionId(1));
        assertThat(relay.openConnections()).isEmpty();
    }

    @Test
    void reportsAnUnreachableDestinationAndClosesTheClient() throws Exception {
        RunningRelay relay = startRelay(Endpoint.of("127.0.0.1", TestServers.closedPort()));
        Socket client = connect(relay);

        assertThat(client.getInputStream().read()).isEqualTo(-1);
        TrafficEvent.ConnectionFailed failed = (TrafficEvent.ConnectionFailed) awaitEvent(
                e -> e instanceof TrafficEvent.ConnectionFailed);
        assertThat(failed.side()).isEqualTo(Side.DESTINATION);
        assertThat(failed.stage()).isEqualTo(FailureStage.CONNECT);
        assertThat(failed.message()).startsWith("Cannot connect to 127.0.0.1:");
        TrafficEvent.ConnectionClosed closed = (TrafficEvent.ConnectionClosed) awaitEvent(
                e -> e instanceof TrafficEvent.ConnectionClosed);
        assertThat(closed.reason()).isEqualTo("destination unreachable");
        assertThat(kinds(Side.DESTINATION)).containsExactly(EventKind.FAILED);
    }

    @Test
    void keepsLargeTransfersByteExact() throws Exception {
        TestServers.TransformServer server = TestServers.TransformServer.echo();
        toClose.add(server);
        RunningRelay relay = startRelay(server.endpoint());
        byte[] data = new byte[8 * 1024 * 1024];
        new Random(1).nextBytes(data);

        Socket client = connect(relay);
        try (ExecutorService writer = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> write = writer.submit(() -> {
                client.getOutputStream().write(data);
                client.shutdownOutput();
                return null;
            });
            byte[] echoed = client.getInputStream().readAllBytes();
            write.get();
            assertThat(sha256(echoed)).isEqualTo(sha256(data));
        }
        TrafficEvent.ConnectionClosed closed = (TrafficEvent.ConnectionClosed) awaitEvent(
                e -> e instanceof TrafficEvent.ConnectionClosed c && c.side() == Side.SOURCE);
        assertThat(closed.bytesIn()).isEqualTo(data.length);
        assertThat(closed.bytesOut()).isEqualTo(data.length);
        long captured = events.stream().filter(e -> e.side() == Side.SOURCE && e instanceof TrafficEvent.DataTransferred d
                && d.direction() == Direction.INBOUND).mapToLong(TrafficEvent::byteCount).sum();
        assertThat(captured).isEqualTo(data.length);
    }

    @Test
    void handlesManyConcurrentClients() throws Exception {
        TestServers.TransformServer server = TestServers.TransformServer.echo();
        toClose.add(server);
        RunningRelay relay = startRelay(server.endpoint());
        int clients = 100;
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> replies = new ArrayList<>();
            for (int i = 0; i < clients; i++) {
                String message = "client-" + i;
                replies.add(pool.submit(() -> {
                    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), relay.listeningOn().port().value())) {
                        socket.getOutputStream().write(message.getBytes(StandardCharsets.US_ASCII));
                        socket.shutdownOutput();
                        return new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
                    }
                }));
            }
            for (int i = 0; i < clients; i++) {
                assertThat(replies.get(i).get()).isEqualTo("client-" + i);
            }
        }
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (closedCount(Side.SOURCE) < clients && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(closedCount(Side.SOURCE)).isEqualTo(clients);
        assertThat(events.stream().filter(e -> e instanceof TrafficEvent.ConnectionOpened && e.side() == Side.SOURCE)
                .map(e -> e.connectionId().orElseThrow()).distinct().count()).isEqualTo(clients);
    }

    @Test
    void sendsUserBytesToEitherSide() throws Exception {
        TestServers.SinkServer server = new TestServers.SinkServer();
        toClose.add(server);
        RunningRelay relay = startRelay(server.endpoint());
        Socket client = connect(relay);
        awaitEvent(e -> e instanceof TrafficEvent.ConnectionOpened && e.side() == Side.DESTINATION);
        ConnectionId id = new ConnectionId(1);

        relay.send(id, Side.DESTINATION, Payload.copyOf(new byte[] {0x02, 0x00, 0x03}));
        relay.send(id, Side.SOURCE, Payload.copyOf("hi".getBytes(StandardCharsets.US_ASCII)));

        assertThat(client.getInputStream().readNBytes(2)).isEqualTo("hi".getBytes(StandardCharsets.US_ASCII));
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (server.received().length < 3 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(server.received()).containsExactly(0x02, 0x00, 0x03);
        assertThat(events).filteredOn(e -> e instanceof TrafficEvent.DataTransferred d && d.origin() == DataOrigin.INJECTED)
                .extracting(TrafficEvent::side).containsExactlyInAnyOrder(Side.DESTINATION, Side.SOURCE);
        assertThatThrownBy(() -> relay.send(new ConnectionId(99), Side.SOURCE, Payload.empty()))
                .isInstanceOf(UnknownConnectionException.class);
    }

    @Test
    void disconnectingClosesBothLegs() throws Exception {
        TestServers.SinkServer server = new TestServers.SinkServer();
        toClose.add(server);
        RunningRelay relay = startRelay(server.endpoint());
        Socket client = connect(relay);
        awaitEvent(e -> e instanceof TrafficEvent.ConnectionOpened && e.side() == Side.DESTINATION);

        relay.disconnect(new ConnectionId(1));

        assertThat(readsEndOrReset(client.getInputStream())).isTrue();
        TrafficEvent.ConnectionClosed closed = (TrafficEvent.ConnectionClosed) awaitEvent(
                e -> e instanceof TrafficEvent.ConnectionClosed c && c.side() == Side.SOURCE);
        assertThat(closed.reason()).isEqualTo("disconnected by user");
        assertThat(events).noneMatch(e -> e instanceof TrafficEvent.ConnectionFailed);
    }

    @Test
    void stoppingClosesOpenConnections() throws Exception {
        TestServers.SinkServer server = new TestServers.SinkServer();
        toClose.add(server);
        RunningRelay relay = startRelay(server.endpoint());
        Socket client = connect(relay);
        awaitEvent(e -> e instanceof TrafficEvent.ConnectionOpened && e.side() == Side.DESTINATION);

        relay.close();

        assertThat(readsEndOrReset(client.getInputStream())).isTrue();
        assertThat(events).filteredOn(e -> e instanceof TrafficEvent.ConnectionClosed)
                .extracting(e -> ((TrafficEvent.ConnectionClosed) e).reason())
                .containsExactly("proxy stopped", "proxy stopped");
        assertThat(relay.openConnections()).isEmpty();
        assertThatThrownBy(() -> new Socket(InetAddress.getLoopbackAddress(), relay.listeningOn().port().value()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void newDestinationAppliesToNewConnections() throws Exception {
        TestServers.TransformServer first = new TestServers.TransformServer("127.0.0.1", b -> "A".getBytes());
        TestServers.TransformServer second = new TestServers.TransformServer("127.0.0.1", b -> "B".getBytes());
        toClose.add(first);
        toClose.add(second);
        RunningRelay relay = startRelay(first.endpoint());

        assertThat(roundTrip(relay)).isEqualTo("A");
        relay.changeDestination(second.endpoint());
        assertThat(roundTrip(relay)).isEqualTo("B");
    }

    @Test
    void relaysOverIpv6WhenAvailable() throws Exception {
        boolean ipv6;
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0, 1, InetAddress.getByName("::1"))) {
            ipv6 = probe.isBound();
        } catch (IOException e) {
            ipv6 = false;
        }
        assumeThat(ipv6).as("IPv6 loopback available").isTrue();
        TestServers.TransformServer server = new TestServers.TransformServer("::1", b -> b);
        toClose.add(server);
        RunningRelay relay = engine.start(ProxyConfiguration.of(new Endpoint(IpAddress.parse("::1"), new Port(0)),
                server.endpoint()), events::add);
        toClose.add(relay);
        try (Socket client = new Socket(InetAddress.getByName("::1"), relay.listeningOn().port().value())) {
            client.getOutputStream().write(new byte[] {1, 2, 3});
            client.shutdownOutput();
            assertThat(client.getInputStream().readAllBytes()).containsExactly(1, 2, 3);
        }
        assertThat(relay.listeningOn().toString()).startsWith("[::1]:");
    }

    @Test
    void refusesAPortThatIsInUse() throws Exception {
        RunningRelay relay = startRelay(Endpoint.of("127.0.0.1", TestServers.closedPort()));
        assertThatThrownBy(() -> engine.start(ProxyConfiguration.of(relay.listeningOn(),
                Endpoint.of("127.0.0.1", 1)), events::add)).isInstanceOf(BindException.class);
    }

    private String roundTrip(RunningRelay relay) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), relay.listeningOn().port().value())) {
            socket.getOutputStream().write('x');
            socket.shutdownOutput();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
        }
    }

    private static boolean readsEndOrReset(InputStream in) {
        try {
            return in.read() == -1;
        } catch (IOException reset) {
            return true;
        }
    }

    private long closedCount(Side side) {
        return events.stream().filter(e -> e instanceof TrafficEvent.ConnectionClosed && e.side() == side).count();
    }

    private String data(Side side, Direction direction) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        events.stream().filter(e -> e instanceof TrafficEvent.DataTransferred d && d.side() == side
                        && d.direction() == direction)
                .forEach(e -> bytes.writeBytes(((TrafficEvent.DataTransferred) e).payload().toByteArray()));
        return bytes.toString(StandardCharsets.US_ASCII);
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private List<EventKind> kinds(Side side) {
        return events.stream().filter(e -> e.side() == side).map(TrafficEvent::kind).toList();
    }

    private TrafficEvent awaitEvent(Predicate<TrafficEvent> condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            for (TrafficEvent event : events) {
                if (condition.test(event)) {
                    return event;
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("event did not arrive; got " + events);
    }
}
