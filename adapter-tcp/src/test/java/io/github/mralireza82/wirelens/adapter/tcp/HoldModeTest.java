package io.github.mralireza82.wirelens.adapter.tcp;

import io.github.mralireza82.wirelens.application.intercept.HoldService;
import io.github.mralireza82.wirelens.application.port.out.RunningRelay;
import io.github.mralireza82.wirelens.domain.intercept.HeldMessage;
import io.github.mralireza82.wirelens.domain.net.Endpoint;
import io.github.mralireza82.wirelens.domain.net.IpAddress;
import io.github.mralireza82.wirelens.domain.net.Port;
import io.github.mralireza82.wirelens.domain.proxy.ProxyConfiguration;
import io.github.mralireza82.wirelens.domain.traffic.DataOrigin;
import io.github.mralireza82.wirelens.domain.traffic.Direction;
import io.github.mralireza82.wirelens.domain.traffic.Payload;
import io.github.mralireza82.wirelens.domain.traffic.Side;
import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Hold mode end to end: the real TCP relay consulting the real {@link HoldService}. */
@Timeout(20)
class HoldModeTest {
    private final List<TrafficEvent> events = new CopyOnWriteArrayList<>();
    private final HoldService hold = new HoldService(events::add, new TcpOneShotSender(Duration.ofSeconds(2)),
            Clock.systemUTC());
    private final TcpRelayEngine engine = new TcpRelayEngine(Clock.systemUTC(), hold);
    private final List<AutoCloseable> toClose = new ArrayList<>();
    private TestServers.TransformServer echo;
    private RunningRelay relay;

    @AfterEach
    void closeEverything() throws Exception {
        for (AutoCloseable closeable : toClose.reversed()) {
            closeable.close();
        }
    }

    private Socket startAndConnect() throws IOException {
        echo = TestServers.TransformServer.echo();
        toClose.add(echo);
        relay = engine.start(ProxyConfiguration.of(new Endpoint(IpAddress.loopbackIpv4(), new Port(0)),
                echo.endpoint()), events::add);
        toClose.add(relay);
        Socket client = new Socket(InetAddress.getLoopbackAddress(), relay.listeningOn().port().value());
        client.setSoTimeout(5_000);
        toClose.add(client);
        return client;
    }

    @Test
    void holdsUntilForwardedAndKeepsOtherDirectionFlowing() throws Exception {
        hold.setHolding(Side.SOURCE, true);
        Socket client = startAndConnect();

        send(client, "PING");
        HeldMessage held = awaitHeld(1).getFirst();
        assertThat(ascii(held.payload())).isEqualTo("PING");
        assertThat(held.headingTo()).isEqualTo(Side.DESTINATION);
        assertThat(echo.accepted).hasSize(1);
        assertNothingArrives(client);

        assertThat(hold.forward(held.id())).isTrue();
        assertThat(read(client, 4)).isEqualTo("PING");
        assertThat(hold.forward(held.id())).as("a decision is taken once").isFalse();
        assertThat(hold.held()).isEmpty();
    }

    @Test
    void forwardsEditedBytesAndMarksThemEdited() throws Exception {
        hold.setHolding(Side.SOURCE, true);
        Socket client = startAndConnect();

        send(client, "0200");
        HeldMessage held = awaitHeld(1).getFirst();
        hold.forwardEdited(held.id(), Payload.copyOf("0210".getBytes(StandardCharsets.US_ASCII)));

        assertThat(read(client, 4)).isEqualTo("0210");
        await(() -> events.stream().anyMatch(e -> e instanceof TrafficEvent.DataTransferred d
                && d.side() == Side.DESTINATION && d.direction() == Direction.OUTBOUND
                && d.origin() == DataOrigin.EDITED));
    }

    @Test
    void droppedMessagesNeverReachTheOtherSide() throws Exception {
        hold.setHolding(Side.SOURCE, true);
        Socket client = startAndConnect();

        send(client, "AAAA");
        hold.drop(awaitHeld(1).getFirst().id());
        send(client, "BBBB");
        hold.forward(awaitHeld(1).getFirst().id());

        assertThat(read(client, 4)).isEqualTo("BBBB");
    }

    @Test
    void turningHoldOffForwardsWhatWaits() throws Exception {
        hold.setHolding(Side.SOURCE, true);
        Socket client = startAndConnect();
        send(client, "WAIT");
        awaitHeld(1);

        hold.setHolding(Side.SOURCE, false);

        assertThat(read(client, 4)).isEqualTo("WAIT");
        send(client, "FREE");
        assertThat(read(client, 4)).isEqualTo("FREE");
        assertThat(hold.held()).isEmpty();
    }

    @Test
    void holdsRepliesFromTheDestinationToo() throws Exception {
        hold.setHolding(Side.DESTINATION, true);
        Socket client = startAndConnect();

        send(client, "ECHO");
        HeldMessage reply = awaitHeld(1).getFirst();
        assertThat(reply.receivedOn()).isEqualTo(Side.DESTINATION);
        assertNothingArrives(client);
        hold.forward(reply.id());
        assertThat(read(client, 4)).isEqualTo("ECHO");
    }

    @Test
    void disconnectingReleasesHeldMessages() throws Exception {
        hold.setHolding(Side.SOURCE, true);
        Socket client = startAndConnect();
        send(client, "STUCK");
        HeldMessage held = awaitHeld(1).getFirst();

        relay.disconnect(held.connection());

        await(() -> hold.held().isEmpty());
        await(() -> relay.openConnections().isEmpty());
        assertThat(client.getInputStream().read()).isEqualTo(-1);
    }

    @Test
    void sendsACopyElsewhereAndReturnsTheReply() throws Exception {
        hold.setHolding(Side.SOURCE, true);
        Socket client = startAndConnect();
        TestServers.TransformServer other = new TestServers.TransformServer("127.0.0.1",
                bytes -> ("ACK:" + new String(bytes, StandardCharsets.US_ASCII)).getBytes(StandardCharsets.US_ASCII));
        toClose.add(other);
        send(client, "COPY");
        HeldMessage held = awaitHeld(1).getFirst();

        Payload reply = hold.sendCopy(held.id(), held.payload(), other.endpoint());

        assertThat(ascii(reply)).isEqualTo("ACK:COPY");
        assertThat(hold.held()).as("the original stays held").hasSize(1);
        assertThat(events).anyMatch(e -> e instanceof TrafficEvent.Notice n && n.message().contains("reply 8 B"));
    }

    @Test
    void reportsACopyThatCannotBeDelivered() throws Exception {
        hold.setHolding(Side.SOURCE, true);
        Socket client = startAndConnect();
        send(client, "COPY");
        HeldMessage held = awaitHeld(1).getFirst();

        assertThatThrownBy(() -> hold.sendCopy(held.id(), held.payload(),
                Endpoint.of("127.0.0.1", TestServers.closedPort()))).isInstanceOf(IOException.class);
        assertThat(events).anyMatch(e -> e instanceof TrafficEvent.Notice n
                && n.message().startsWith("Could not send a copy"));
    }

    // ------------------------------------------------------------------ helpers

    private static void send(Socket client, String text) throws IOException {
        client.getOutputStream().write(text.getBytes(StandardCharsets.US_ASCII));
        client.getOutputStream().flush();
    }

    private static String read(Socket client, int length) throws IOException {
        return new String(client.getInputStream().readNBytes(length), StandardCharsets.US_ASCII);
    }

    private static String ascii(Payload payload) {
        return new String(payload.toByteArray(), StandardCharsets.US_ASCII);
    }

    private static void assertNothingArrives(Socket client) throws IOException {
        client.setSoTimeout(300);
        try {
            int value = client.getInputStream().read();
            throw new AssertionError("expected nothing, got byte " + value);
        } catch (SocketTimeoutException expected) {
            // nothing was forwarded
        } finally {
            client.setSoTimeout(5_000);
        }
    }

    private List<HeldMessage> awaitHeld(int count) throws InterruptedException {
        await(() -> hold.held().size() == count);
        return hold.held();
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("condition not met in time");
    }
}
