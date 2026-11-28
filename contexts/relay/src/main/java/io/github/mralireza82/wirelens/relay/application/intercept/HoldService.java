package io.github.mralireza82.wirelens.relay.application.intercept;

import io.github.mralireza82.wirelens.relay.application.port.in.HoldCommands;
import io.github.mralireza82.wirelens.relay.application.port.out.OneShotSender;
import io.github.mralireza82.wirelens.relay.application.port.out.TrafficEventSink;
import io.github.mralireza82.wirelens.relay.application.port.out.TrafficGate;
import io.github.mralireza82.wirelens.relay.domain.intercept.HeldMessage;
import io.github.mralireza82.wirelens.relay.domain.intercept.HoldDecision;
import io.github.mralireza82.wirelens.relay.domain.intercept.HoldId;
import io.github.mralireza82.wirelens.kernel.net.Endpoint;
import io.github.mralireza82.wirelens.kernel.id.ConnectionId;
import io.github.mralireza82.wirelens.relay.domain.traffic.Link;
import io.github.mralireza82.wirelens.kernel.bytes.Payload;
import io.github.mralireza82.wirelens.relay.domain.traffic.Side;
import io.github.mralireza82.wirelens.relay.domain.traffic.TrafficEvent;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/**
 * Hold mode. Acts as the relay's {@link TrafficGate} and as the UI's {@link HoldCommands}: while holding is on for a
 * side, each message received there parks the relay thread of that connection and direction on a future until the
 * user decides. Decisions complete the future; the relay thread then forwards or discards.
 *
 * <p>Thread safety: state is guarded by one lock that is never held while blocking or calling listeners. A lock
 * rather than {@code synchronized}, so waiting virtual threads never pin their carrier.
 */
public final class HoldService implements TrafficGate, HoldCommands {
    /** How long {@link #sendCopy} waits for a reply after the peer went quiet. */
    public static final Duration COPY_REPLY_TIMEOUT = Duration.ofSeconds(3);

    private record Pending(HeldMessage message, CompletableFuture<HoldDecision> decision) {
    }

    private final TrafficEventSink sink;
    private final OneShotSender oneShot;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<HoldId, Pending> pending = new LinkedHashMap<>();
    private final Set<Side> holding = EnumSet.noneOf(Side.class);
    private final AtomicLong nextId = new AtomicLong();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public HoldService(TrafficEventSink sink, OneShotSender oneShot, Clock clock) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.oneShot = Objects.requireNonNull(oneShot, "oneShot");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ------------------------------------------------------------------ TrafficGate (relay threads)

    @Override
    public HoldDecision admit(Link from, Payload payload) throws InterruptedException {
        Pending entry;
        lock.lock();
        try {
            if (!holding.contains(from.side())) {
                return HoldDecision.pass(payload);
            }
            HeldMessage message = new HeldMessage(new HoldId(nextId.incrementAndGet()), clock.instant(), from,
                    payload);
            entry = new Pending(message, new CompletableFuture<>());
            pending.put(message.id(), entry);
        } finally {
            lock.unlock();
        }
        changed();
        try {
            return entry.decision().get();
        } catch (ExecutionException e) {
            throw new IllegalStateException("hold decision failed", e.getCause());
        } catch (InterruptedException e) {
            remove(entry.message().id());
            throw e;
        }
    }

    @Override
    public void release(ConnectionId connection) {
        List<Pending> released = removeAll(p -> p.message().connection().equals(connection));
        released.forEach(p -> p.decision().complete(new HoldDecision.Drop()));
        if (!released.isEmpty()) {
            changed();
        }
    }

    // ------------------------------------------------------------------ HoldCommands (UI)

    @Override
    public void setHolding(Side receivedOn, boolean on) {
        lock.lock();
        try {
            if (on ? !holding.add(receivedOn) : !holding.remove(receivedOn)) {
                return;
            }
        } finally {
            lock.unlock();
        }
        if (!on) {
            forwardAll(receivedOn);
        }
        sink.publish(new TrafficEvent.Notice(clock.instant(), receivedOn, on
                ? "Hold on: messages from the " + peer(receivedOn) + " wait for your decision"
                : "Hold off: messages from the " + peer(receivedOn) + " flow again"));
        changed();
    }

    @Override
    public boolean isHolding(Side receivedOn) {
        lock.lock();
        try {
            return holding.contains(receivedOn);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<HeldMessage> held() {
        lock.lock();
        try {
            return pending.values().stream().map(Pending::message).toList();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onChange(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public boolean forward(HoldId id) {
        return remove(id).map(p -> {
            p.decision().complete(HoldDecision.pass(p.message().payload()));
            changed();
            return true;
        }).orElse(false);
    }

    @Override
    public boolean forwardEdited(HoldId id, Payload replacement) {
        Objects.requireNonNull(replacement, "replacement");
        return remove(id).map(p -> {
            boolean changedBytes = !replacement.equals(p.message().payload());
            p.decision().complete(new HoldDecision.Forward(replacement, changedBytes));
            if (changedBytes) {
                notice(p.message(), "Forwarded " + id + " edited: " + p.message().payload().size() + " B → "
                        + replacement.size() + " B");
            }
            changed();
            return true;
        }).orElse(false);
    }

    @Override
    public boolean drop(HoldId id) {
        return remove(id).map(p -> {
            p.decision().complete(new HoldDecision.Drop());
            notice(p.message(), "Dropped " + id + " (" + p.message().payload().size() + " B from the "
                    + peer(p.message().receivedOn()) + " of " + p.message().connection() + ")");
            changed();
            return true;
        }).orElse(false);
    }

    @Override
    public int forwardAll(Side receivedOn) {
        List<Pending> all = removeAll(p -> p.message().receivedOn() == receivedOn);
        all.forEach(p -> p.decision().complete(HoldDecision.pass(p.message().payload())));
        if (!all.isEmpty()) {
            changed();
        }
        return all.size();
    }

    @Override
    public int dropAll(Side receivedOn) {
        List<Pending> all = removeAll(p -> p.message().receivedOn() == receivedOn);
        all.forEach(p -> p.decision().complete(new HoldDecision.Drop()));
        if (!all.isEmpty()) {
            sink.publish(new TrafficEvent.Notice(clock.instant(), receivedOn,
                    "Dropped " + all.size() + " held message" + (all.size() == 1 ? "" : "s")));
            changed();
        }
        return all.size();
    }

    @Override
    public Payload sendCopy(HoldId id, Payload bytes, Endpoint target) throws IOException {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(target, "target");
        HeldMessage message = find(id).orElse(null);
        Side side = message == null ? Side.SOURCE : message.receivedOn();
        try {
            Payload reply = oneShot.exchange(target, bytes, COPY_REPLY_TIMEOUT);
            sink.publish(new TrafficEvent.Notice(clock.instant(), side, "Sent a copy of " + id + " (" + bytes.size()
                    + " B) to " + target + " · reply " + reply.size() + " B"
                    + (reply.isEmpty() ? "" : ": " + preview(reply))));
            return reply;
        } catch (IOException e) {
            sink.publish(new TrafficEvent.Notice(clock.instant(), side, "Could not send a copy of " + id + " to "
                    + target + ": " + e.getMessage()));
            throw e;
        }
    }

    // ------------------------------------------------------------------ internals

    private Optional<HeldMessage> find(HoldId id) {
        lock.lock();
        try {
            return Optional.ofNullable(pending.get(id)).map(Pending::message);
        } finally {
            lock.unlock();
        }
    }

    private Optional<Pending> remove(HoldId id) {
        lock.lock();
        try {
            return Optional.ofNullable(pending.remove(id));
        } finally {
            lock.unlock();
        }
    }

    private List<Pending> removeAll(Predicate<Pending> which) {
        List<Pending> removed = new ArrayList<>();
        lock.lock();
        try {
            for (Iterator<Pending> it = pending.values().iterator(); it.hasNext(); ) {
                Pending p = it.next();
                if (which.test(p)) {
                    removed.add(p);
                    it.remove();
                }
            }
        } finally {
            lock.unlock();
        }
        return removed;
    }

    private void changed() {
        listeners.forEach(Runnable::run);
    }

    private void notice(HeldMessage message, String text) {
        sink.publish(new TrafficEvent.Notice(clock.instant(), message.receivedOn(), text));
    }

    private static String peer(Side side) {
        return side == Side.SOURCE ? "client" : "destination";
    }

    private static String preview(Payload payload) {
        StringBuilder hex = new StringBuilder();
        int shown = Math.min(payload.size(), 32);
        for (int i = 0; i < shown; i++) {
            hex.append(i == 0 ? "" : " ").append(String.format("%02X", payload.unsignedAt(i)));
        }
        return payload.size() > shown ? hex + " …" : hex.toString();
    }
}
