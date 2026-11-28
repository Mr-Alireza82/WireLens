package io.github.mralireza82.wirelens.relay.application.intercept;

import io.github.mralireza82.wirelens.relay.domain.intercept.HoldDecision;
import io.github.mralireza82.wirelens.kernel.net.Endpoint;
import io.github.mralireza82.wirelens.kernel.id.ConnectionId;
import io.github.mralireza82.wirelens.relay.domain.traffic.Link;
import io.github.mralireza82.wirelens.kernel.bytes.Payload;
import io.github.mralireza82.wirelens.relay.domain.traffic.Side;
import io.github.mralireza82.wirelens.relay.domain.traffic.TrafficEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(10)
class HoldServiceTest {
    private static final Link CLIENT = new Link(new ConnectionId(1), Side.SOURCE, Endpoint.of("127.0.0.1", 9000),
            Endpoint.of("10.0.0.7", 50123));
    private static final Payload BYTES = Payload.copyOf(new byte[] {2, 0});

    private final List<TrafficEvent> events = new CopyOnWriteArrayList<>();
    private final HoldService hold = new HoldService(events::add, (target, payload, timeout) -> Payload.empty(),
            Clock.systemUTC());

    @Test
    void passesEverythingWhileHoldIsOff() throws Exception {
        assertThat(hold.admit(CLIENT, BYTES)).isEqualTo(HoldDecision.pass(BYTES));
        assertThat(hold.held()).isEmpty();
    }

    @Test
    void holdsOnlyTheSideThatIsSwitchedOn() throws Exception {
        hold.setHolding(Side.DESTINATION, true);
        assertThat(hold.admit(CLIENT, BYTES)).isEqualTo(HoldDecision.pass(BYTES));
        assertThat(hold.isHolding(Side.DESTINATION)).isTrue();
        assertThat(hold.isHolding(Side.SOURCE)).isFalse();
    }

    @Test
    void releasingAConnectionDropsWhatItHeldAndNotifies() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        hold.onChange(changes::incrementAndGet);
        hold.setHolding(Side.SOURCE, true);
        CompletableFuture<HoldDecision> decision = CompletableFuture.supplyAsync(() -> {
            try {
                return hold.admit(CLIENT, BYTES);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });
        while (hold.held().isEmpty()) {
            Thread.sleep(5);
        }

        hold.release(CLIENT.id());

        assertThat(decision.get()).isInstanceOf(HoldDecision.Drop.class);
        assertThat(hold.held()).isEmpty();
        assertThat(changes.get()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void anUnchangedEditIsAPlainForward() throws Exception {
        hold.setHolding(Side.SOURCE, true);
        CompletableFuture<HoldDecision> decision = CompletableFuture.supplyAsync(() -> {
            try {
                return hold.admit(CLIENT, BYTES);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });
        while (hold.held().isEmpty()) {
            Thread.sleep(5);
        }

        hold.forwardEdited(hold.held().getFirst().id(), Payload.copyOf(new byte[] {2, 0}));

        assertThat(decision.get()).isEqualTo(new HoldDecision.Forward(BYTES, false));
    }

    @Test
    void announcesSwitchingInThePanel() {
        hold.setHolding(Side.SOURCE, true);
        hold.setHolding(Side.SOURCE, true);
        hold.setHolding(Side.SOURCE, false);
        assertThat(events).hasSize(2).allMatch(e -> e instanceof TrafficEvent.Notice n && n.side() == Side.SOURCE);
    }
}
