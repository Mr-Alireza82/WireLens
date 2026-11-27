package io.github.mralireza82.wirelens.adapter.tcp;

import io.github.mralireza82.wirelens.application.port.out.TrafficEventSink;
import io.github.mralireza82.wirelens.application.port.out.TrafficGate;
import io.github.mralireza82.wirelens.domain.intercept.HoldDecision;
import io.github.mralireza82.wirelens.application.port.out.UnknownConnectionException;
import io.github.mralireza82.wirelens.domain.net.Endpoint;
import io.github.mralireza82.wirelens.domain.proxy.ProxyConfiguration;
import io.github.mralireza82.wirelens.domain.traffic.ConnectionId;
import io.github.mralireza82.wirelens.domain.traffic.DataOrigin;
import io.github.mralireza82.wirelens.domain.traffic.Direction;
import io.github.mralireza82.wirelens.domain.traffic.FailureStage;
import io.github.mralireza82.wirelens.domain.traffic.Link;
import io.github.mralireza82.wirelens.domain.traffic.Payload;
import io.github.mralireza82.wirelens.domain.traffic.Side;
import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;

import java.io.IOException;
import java.net.Socket;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One client relayed to the destination.
 *
 * <p>Life cycle: client accepted, then connect to the destination, then two pumps run until both directions have
 * ended (each end-of-stream is passed on as a half-close, so request/response protocols that close their sending
 * side early still work), then both sockets are closed and one closed event per leg is published. The first
 * thing that ends the connection (a peer closing, an error, the user, the proxy stopping) becomes its reason.
 */
final class RelayedConnection {
    private static final System.Logger LOG = System.getLogger(RelayedConnection.class.getName());

    private final ConnectionId id;
    private final Socket clientSocket;
    private final Endpoint destination;
    private final ProxyConfiguration configuration;
    private final TrafficEventSink sink;
    private final TrafficGate gate;
    private final Clock clock;
    private final ExecutorService threads;
    private final Runnable onFinished;
    /** Why the connection ended; the first cause wins, except that an error or an explicit stop replaces an EOF. */
    private final AtomicReference<String> endReason = new AtomicReference<>();
    /** Set once sockets are being closed on purpose, after which I/O errors are expected and not reported. */
    private final AtomicBoolean tearingDown = new AtomicBoolean();

    private volatile Leg source;
    private volatile Leg target;
    private volatile Socket pendingUpstream;

    RelayedConnection(ConnectionId id, Socket clientSocket, Endpoint destination, ProxyConfiguration configuration,
                      TrafficEventSink sink, TrafficGate gate, Clock clock, ExecutorService threads,
                      Runnable onFinished) {
        this.id = id;
        this.clientSocket = clientSocket;
        this.destination = destination;
        this.configuration = configuration;
        this.sink = sink;
        this.gate = gate;
        this.clock = clock;
        this.threads = threads;
        this.onFinished = onFinished;
    }

    void run() {
        try {
            clientSocket.setTcpNoDelay(true);
            Link sourceLink = new Link(id, Side.SOURCE, Sockets.toEndpoint(clientSocket.getLocalSocketAddress()),
                    Sockets.toEndpoint(clientSocket.getRemoteSocketAddress()));
            source = new Leg(clientSocket, sourceLink, clock.instant());
            sink.publish(new TrafficEvent.ConnectionOpened(clock.instant(), sourceLink));

            if (!connectToDestination()) {
                return;
            }
            Future<?> downstream = threads.submit(() -> pump(target, source));
            pump(source, target);
            awaitQuietly(downstream);
        } catch (IOException | RejectedExecutionException e) {
            fail(Side.SOURCE, FailureStage.ACCEPT, e);
        } finally {
            finish();
        }
    }

    private boolean connectToDestination() {
        Socket upstream = new Socket();
        pendingUpstream = upstream;
        try {
            if (tearingDown.get()) {
                return false;
            }
            upstream.setTcpNoDelay(true);
            upstream.connect(Sockets.toSocketAddress(destination), (int) configuration.connectTimeout().toMillis());
            Link link = new Link(id, Side.DESTINATION, Sockets.toEndpoint(upstream.getLocalSocketAddress()),
                    Sockets.toEndpoint(upstream.getRemoteSocketAddress()));
            target = new Leg(upstream, link, clock.instant());
            sink.publish(new TrafficEvent.ConnectionOpened(clock.instant(), link));
            return true;
        } catch (IOException e) {
            TcpRelay.closeQuietly(upstream);
            if (tearingDown.compareAndSet(false, true)) {
                endReason.set("destination unreachable");
                sink.publish(new TrafficEvent.ConnectionFailed(clock.instant(), Side.DESTINATION, Optional.of(id),
                        Optional.of(destination), FailureStage.CONNECT,
                        "Cannot connect to " + destination + ": " + Sockets.describe(e)));
            }
            return false;
        } finally {
            pendingUpstream = null;
        }
    }

    /**
     * Copies bytes from one leg to the other until end of stream or failure. Each chunk passes the gate first, which
     * may hold it (blocking only this direction of this connection) until the user forwards, edits or drops it.
     */
    private void pump(Leg from, Leg to) {
        byte[] buffer = new byte[configuration.bufferSize()];
        while (true) {
            int count;
            try {
                count = from.read(buffer);
            } catch (IOException e) {
                fail(from.link().side(), FailureStage.READ, e);
                return;
            }
            if (count < 0) {
                endReason.compareAndSet(null, from.link().side() == Side.SOURCE
                        ? "client closed the connection" : "destination closed the connection");
                sink.publish(new TrafficEvent.StreamEnded(clock.instant(), from.link(), Direction.INBOUND));
                try {
                    to.shutdownOutput();
                    sink.publish(new TrafficEvent.StreamEnded(clock.instant(), to.link(), Direction.OUTBOUND));
                } catch (IOException e) {
                    fail(to.link().side(), FailureStage.WRITE, e);
                }
                return;
            }
            Payload payload = Payload.copyOf(buffer, 0, count);
            sink.publish(new TrafficEvent.DataTransferred(clock.instant(), from.link(), Direction.INBOUND, payload,
                    DataOrigin.RELAYED));
            HoldDecision decision;
            try {
                decision = gate.admit(from.link(), payload);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!(decision instanceof HoldDecision.Forward forward)) {
                continue;
            }
            if (tearingDown.get()) {
                return;
            }
            try {
                to.write(forward.payload(), forward.edited() ? DataOrigin.EDITED : DataOrigin.RELAYED, sink, clock);
            } catch (IOException e) {
                fail(to.link().side(), FailureStage.WRITE, e);
                return;
            }
        }
    }

    /** Sends user-supplied bytes on one leg. */
    void send(Side side, Payload payload) throws IOException {
        Leg leg = side == Side.SOURCE ? source : target;
        if (leg == null || tearingDown.get()) {
            throw new UnknownConnectionException(id);
        }
        try {
            leg.write(payload, DataOrigin.INJECTED, sink, clock);
        } catch (IOException e) {
            sink.publish(new TrafficEvent.ConnectionFailed(clock.instant(), side, Optional.of(id),
                    Optional.of(leg.link().remote()), FailureStage.SEND, "Send failed: " + Sockets.describe(e)));
            throw e;
        }
    }

    /** Ends the connection from outside (user or proxy shutdown). The pumps notice and finish. */
    void terminate(String reason) {
        if (tearingDown.compareAndSet(false, true)) {
            endReason.set(reason);
        }
        closeSockets();
    }

    /** Reports a failure, unless the connection is already being torn down (then errors are expected noise). */
    private void fail(Side side, FailureStage stage, Exception error) {
        if (tearingDown.compareAndSet(false, true)) {
            endReason.set("error: " + Sockets.describe(error));
            Leg leg = side == Side.SOURCE ? source : target;
            Optional<Endpoint> remote = leg == null ? Optional.empty() : Optional.of(leg.link().remote());
            sink.publish(new TrafficEvent.ConnectionFailed(clock.instant(), side, Optional.of(id), remote, stage,
                    Sockets.describe(error)));
            LOG.log(System.Logger.Level.DEBUG, () -> id + " " + stage + " failed", error);
        }
        closeSockets();
    }

    private void closeSockets() {
        gate.release(id);
        TcpRelay.closeQuietly(pendingUpstream);
        Leg s = source;
        Leg t = target;
        if (s != null) {
            s.close();
        } else {
            TcpRelay.closeQuietly(clientSocket);
        }
        if (t != null) {
            t.close();
        }
    }

    private void finish() {
        endReason.compareAndSet(null, "closed");
        closeSockets();
        String reason = endReason.get();
        Leg t = target;
        Leg s = source;
        if (t != null) {
            sink.publish(t.closedEvent(clock.instant(), reason));
        }
        if (s != null) {
            sink.publish(s.closedEvent(clock.instant(), reason));
        }
        onFinished.run();
    }

    private static void awaitQuietly(Future<?> future) {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            LOG.log(System.Logger.Level.ERROR, "pump crashed", e.getCause());
        }
    }
}
