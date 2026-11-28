package io.github.mralireza82.wirelens.adapter.gui.javafx;

import io.github.mralireza82.wirelens.relay.application.port.out.TrafficEventSink;
import io.github.mralireza82.wirelens.relay.domain.traffic.Side;
import io.github.mralireza82.wirelens.relay.domain.traffic.TrafficEvent;
import javafx.animation.AnimationTimer;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Moves events from network threads to the JavaFX thread in batches.
 *
 * <p>Network threads only append to a lock-free queue. A pulse timer on the FX thread drains it at most
 * {@value #MAX_DRAINS_PER_SECOND} times per second, so ten thousand events per second cost a handful of UI
 * updates, not ten thousand {@code Platform.runLater} calls. The queue is bounded: under extreme load, data
 * events beyond the bound are counted and reported instead of exhausting memory (statistics, which are fed
 * directly, stay exact).
 */
public final class FxEventBridge implements TrafficEventSink {
    static final int MAX_DRAINS_PER_SECOND = 15;
    private static final long DRAIN_INTERVAL_NANOS = 1_000_000_000L / MAX_DRAINS_PER_SECOND;
    private static final int MAX_BATCH = 25_000;

    private final Queue<TrafficEvent> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicLong queuedBytes = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final int capacity;
    private final long byteCapacity;
    private final Clock clock;
    private final List<Consumer<List<TrafficEvent>>> listeners = new CopyOnWriteArrayList<>();
    private final AnimationTimer timer = new AnimationTimer() {
        private long lastDrain;

        @Override
        public void handle(long now) {
            if (now - lastDrain >= DRAIN_INTERVAL_NANOS) {
                lastDrain = now;
                drain();
            }
        }
    };

    /**
     * @param capacity     most events waiting for the UI
     * @param byteCapacity most payload bytes waiting for the UI
     */
    public FxEventBridge(int capacity, long byteCapacity, Clock clock) {
        this.capacity = capacity;
        this.byteCapacity = byteCapacity;
        this.clock = clock;
    }

    /** Called from any thread. */
    @Override
    public void publish(TrafficEvent event) {
        if (event instanceof TrafficEvent.DataTransferred
                && (queued.get() >= capacity || queuedBytes.get() >= byteCapacity)) {
            dropped.incrementAndGet();
            return;
        }
        queuedBytes.addAndGet(event.byteCount());
        queued.incrementAndGet();
        queue.add(event);
    }

    /** Registers a batch consumer; batches are delivered on the FX thread. */
    public void subscribe(Consumer<List<TrafficEvent>> listener) {
        listeners.add(listener);
    }

    public void start() {
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    /** Delivers whatever is queued now. Must run on the FX thread (public for tests and shutdown). */
    public void drain() {
        List<TrafficEvent> batch = new ArrayList<>(Math.min(queued.get(), MAX_BATCH));
        TrafficEvent event;
        long bytes = 0;
        while (batch.size() < MAX_BATCH && (event = queue.poll()) != null) {
            batch.add(event);
            bytes += event.byteCount();
        }
        queuedBytes.addAndGet(-bytes);
        queued.addAndGet(-batch.size());
        long lost = dropped.getAndSet(0);
        if (lost > 0) {
            batch.add(new TrafficEvent.Notice(clock.instant(), Side.SOURCE, lost
                    + " data events were not displayed because traffic arrived faster than the view can show it"
                    + " (statistics still include them)"));
        }
        if (!batch.isEmpty()) {
            List<TrafficEvent> delivered = List.copyOf(batch);
            listeners.forEach(listener -> listener.accept(delivered));
        }
    }
}
