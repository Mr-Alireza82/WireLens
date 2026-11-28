package io.github.mralireza82.wirelens.relay.application.proxy;

import io.github.mralireza82.wirelens.relay.application.port.in.ConnectionCommands;
import io.github.mralireza82.wirelens.relay.application.port.in.ProxyControl;
import io.github.mralireza82.wirelens.relay.application.port.out.RelayEngine;
import io.github.mralireza82.wirelens.relay.application.port.out.RunningRelay;
import io.github.mralireza82.wirelens.relay.application.port.out.TrafficEventSink;
import io.github.mralireza82.wirelens.relay.application.port.out.UnknownConnectionException;
import io.github.mralireza82.wirelens.kernel.net.Endpoint;
import io.github.mralireza82.wirelens.relay.domain.proxy.ProxyConfiguration;
import io.github.mralireza82.wirelens.kernel.id.ConnectionId;
import io.github.mralireza82.wirelens.relay.domain.traffic.FailureStage;
import io.github.mralireza82.wirelens.kernel.bytes.Payload;
import io.github.mralireza82.wirelens.relay.domain.traffic.Side;
import io.github.mralireza82.wirelens.relay.domain.traffic.TrafficEvent;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Application service (facade) behind the start/stop/re-target and connection use cases.
 *
 * <p>It owns the relay life cycle as a small state machine ({@link ProxyState}) and turns infrastructure failures
 * into domain events, so the UI never sees an {@link IOException} from the start button. All state changes happen
 * under the object lock; listeners are notified after the lock is released.
 */
public final class ProxyService implements ProxyControl, ConnectionCommands {
    private static final System.Logger LOG = System.getLogger(ProxyService.class.getName());

    private final RelayEngine engine;
    private final TrafficEventSink sink;
    private final Clock clock;
    private final List<Consumer<ProxyStatus>> listeners = new CopyOnWriteArrayList<>();

    private RunningRelay relay;
    private ProxyStatus status;

    public ProxyService(RelayEngine engine, TrafficEventSink sink, Clock clock) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.status = ProxyStatus.stopped(clock.instant());
    }

    @Override
    public ProxyStatus start(Endpoint listen, Endpoint destination) {
        Objects.requireNonNull(listen, "listen");
        Objects.requireNonNull(destination, "destination");
        synchronized (this) {
            if (!status.state().canStart()) {
                return status;
            }
            status = new ProxyStatus(ProxyState.STARTING, Optional.of(listen), Optional.of(destination),
                    clock.instant(), Optional.empty());
        }
        notifyListeners();
        ProxyStatus result;
        try {
            RunningRelay started = engine.start(ProxyConfiguration.of(listen, destination), sink);
            synchronized (this) {
                relay = started;
                status = new ProxyStatus(ProxyState.RUNNING, Optional.of(started.listeningOn()),
                        Optional.of(destination), clock.instant(), Optional.empty());
                result = status;
            }
            LOG.log(System.Logger.Level.INFO, "Relay started on {0} -> {1}", started.listeningOn(), destination);
            sink.publish(new TrafficEvent.Notice(clock.instant(), Side.SOURCE,
                    "Listening on " + started.listeningOn()));
            sink.publish(new TrafficEvent.Notice(clock.instant(), Side.DESTINATION,
                    "Forwarding new connections to " + destination));
        } catch (IOException | RuntimeException e) {
            String message = describe(e);
            LOG.log(System.Logger.Level.WARNING, "Could not listen on " + listen, e);
            synchronized (this) {
                status = new ProxyStatus(ProxyState.FAILED, Optional.of(listen), Optional.of(destination),
                        clock.instant(), Optional.of(message));
                result = status;
            }
            sink.publish(new TrafficEvent.ConnectionFailed(clock.instant(), Side.SOURCE, Optional.empty(),
                    Optional.of(listen), FailureStage.LISTEN, "Cannot listen on " + listen + ": " + message));
        }
        notifyListeners();
        return result;
    }

    @Override
    public ProxyStatus stop() {
        RunningRelay toClose;
        synchronized (this) {
            if (status.state() != ProxyState.RUNNING) {
                return status;
            }
            toClose = relay;
            relay = null;
            status = new ProxyStatus(ProxyState.STOPPING, status.listen(), status.destination(), clock.instant(),
                    Optional.empty());
        }
        notifyListeners();
        toClose.close();
        ProxyStatus result;
        synchronized (this) {
            status = new ProxyStatus(ProxyState.STOPPED, status.listen(), status.destination(), clock.instant(),
                    Optional.empty());
            result = status;
        }
        LOG.log(System.Logger.Level.INFO, "Relay stopped");
        sink.publish(new TrafficEvent.Notice(clock.instant(), Side.SOURCE, "Stopped listening"));
        notifyListeners();
        return result;
    }

    @Override
    public void changeDestination(Endpoint destination) {
        Objects.requireNonNull(destination, "destination");
        RunningRelay target;
        synchronized (this) {
            if (status.destination().equals(Optional.of(destination))) {
                return;
            }
            status = new ProxyStatus(status.state(), status.listen(), Optional.of(destination), status.since(),
                    status.error());
            target = relay;
        }
        if (target != null) {
            target.changeDestination(destination);
            sink.publish(new TrafficEvent.Notice(clock.instant(), Side.DESTINATION,
                    "Destination changed: new connections go to " + destination));
        }
        notifyListeners();
    }

    @Override
    public synchronized ProxyStatus status() {
        return status;
    }

    @Override
    public void onStatusChanged(Consumer<ProxyStatus> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void send(ConnectionId connection, Side side, Payload payload) throws IOException {
        RunningRelay target = currentRelay();
        if (target == null) {
            throw new UnknownConnectionException(connection);
        }
        target.send(connection, side, payload);
    }

    @Override
    public void disconnect(ConnectionId connection) {
        RunningRelay target = currentRelay();
        if (target != null) {
            target.disconnect(connection);
        }
    }

    @Override
    public Set<ConnectionId> openConnections() {
        RunningRelay target = currentRelay();
        return target == null ? Set.of() : target.openConnections();
    }

    private synchronized RunningRelay currentRelay() {
        return relay;
    }

    private void notifyListeners() {
        ProxyStatus snapshot = status();
        for (Consumer<ProxyStatus> listener : listeners) {
            try {
                listener.accept(snapshot);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR, "Status listener failed", e);
            }
        }
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
