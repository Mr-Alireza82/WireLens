package io.github.mralireza82.wirelens.inspection.application.statistics;

import io.github.mralireza82.wirelens.relay.application.port.out.TrafficEventSink;
import io.github.mralireza82.wirelens.relay.domain.traffic.Direction;
import io.github.mralireza82.wirelens.relay.domain.traffic.Side;
import io.github.mralireza82.wirelens.relay.domain.traffic.TrafficEvent;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Live counters fed by traffic events. Lock-free ({@link LongAdder}) so network threads never contend; readers
 * take cheap {@link StatisticsSnapshot}s whenever they want (the header polls a few times per second).
 *
 * <p>Statistics count everything the relay does, including while the UI capture is paused.
 */
public final class TrafficStatistics implements TrafficEventSink {
    private final Clock clock;
    private final LongAdder bytesFromClients = new LongAdder();
    private final LongAdder bytesToDestination = new LongAdder();
    private final LongAdder bytesFromDestination = new LongAdder();
    private final LongAdder bytesToClients = new LongAdder();
    private final LongAdder totalConnections = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final LongAdder dataEvents = new LongAdder();
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final AtomicInteger peakConnections = new AtomicInteger();

    public TrafficStatistics(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void publish(TrafficEvent event) {
        switch (event) {
            case TrafficEvent.DataTransferred data -> {
                dataEvents.increment();
                counterFor(data.side(), data.direction()).add(data.payload().size());
            }
            case TrafficEvent.ConnectionOpened opened when opened.side() == Side.SOURCE -> {
                totalConnections.increment();
                int active = activeConnections.incrementAndGet();
                peakConnections.accumulateAndGet(active, Math::max);
            }
            case TrafficEvent.ConnectionClosed closed when closed.side() == Side.SOURCE ->
                    activeConnections.updateAndGet(value -> Math.max(0, value - 1));
            case TrafficEvent.ConnectionFailed ignored -> failures.increment();
            default -> {
                // other events carry no counters
            }
        }
    }

    public StatisticsSnapshot snapshot() {
        return new StatisticsSnapshot(clock.instant(), bytesFromClients.sum(), bytesToDestination.sum(),
                bytesFromDestination.sum(), bytesToClients.sum(), activeConnections.get(), peakConnections.get(),
                totalConnections.sum(), failures.sum(), dataEvents.sum());
    }

    /** Clears the totals; connections that are still open stay counted as active. */
    public void resetTotals() {
        bytesFromClients.reset();
        bytesToDestination.reset();
        bytesFromDestination.reset();
        bytesToClients.reset();
        totalConnections.reset();
        failures.reset();
        dataEvents.reset();
        peakConnections.set(activeConnections.get());
    }

    private LongAdder counterFor(Side side, Direction direction) {
        if (side == Side.SOURCE) {
            return direction == Direction.INBOUND ? bytesFromClients : bytesToClients;
        }
        return direction == Direction.INBOUND ? bytesFromDestination : bytesToDestination;
    }
}
