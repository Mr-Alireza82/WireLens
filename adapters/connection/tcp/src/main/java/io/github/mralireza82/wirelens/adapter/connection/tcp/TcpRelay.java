package io.github.mralireza82.wirelens.adapter.connection.tcp;

import io.github.mralireza82.wirelens.relay.application.port.out.RunningRelay;
import io.github.mralireza82.wirelens.relay.application.port.out.TrafficEventSink;
import io.github.mralireza82.wirelens.relay.application.port.out.TrafficGate;
import io.github.mralireza82.wirelens.relay.application.port.out.UnknownConnectionException;
import io.github.mralireza82.wirelens.kernel.net.Endpoint;
import io.github.mralireza82.wirelens.relay.domain.proxy.ProxyConfiguration;
import io.github.mralireza82.wirelens.kernel.id.ConnectionId;
import io.github.mralireza82.wirelens.relay.domain.traffic.FailureStage;
import io.github.mralireza82.wirelens.kernel.bytes.Payload;
import io.github.mralireza82.wirelens.relay.domain.traffic.Side;
import io.github.mralireza82.wirelens.relay.domain.traffic.TrafficEvent;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** A listening relay: accepts clients and runs one {@link RelayedConnection} per client. */
final class TcpRelay implements RunningRelay {
    private static final System.Logger LOG = System.getLogger(TcpRelay.class.getName());
    private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(3);

    private final ServerSocket server;
    private final Endpoint listeningOn;
    private final AtomicReference<Endpoint> destination;
    private final ProxyConfiguration configuration;
    private final TrafficEventSink sink;
    private final TrafficGate gate;
    private final Clock clock;
    private final ExecutorService threads;
    private final AtomicLong nextId = new AtomicLong();
    private final Map<ConnectionId, RelayedConnection> connections = new ConcurrentHashMap<>();
    private volatile boolean closing;

    TcpRelay(ServerSocket server, ProxyConfiguration configuration, TrafficEventSink sink, TrafficGate gate,
             Clock clock, ThreadFactory threadFactory) {
        this.threads = Executors.newThreadPerTaskExecutor(threadFactory);
        this.server = server;
        this.listeningOn = Sockets.toEndpoint(server.getLocalSocketAddress());
        this.destination = new AtomicReference<>(configuration.destination());
        this.configuration = configuration;
        this.sink = sink;
        this.gate = gate;
        this.clock = clock;
    }

    void startAccepting() {
        threads.execute(this::acceptLoop);
    }

    private void acceptLoop() {
        while (!closing) {
            Socket client;
            try {
                client = server.accept();
            } catch (IOException e) {
                if (closing) {
                    return;
                }
                LOG.log(System.Logger.Level.WARNING, "accept failed", e);
                sink.publish(new TrafficEvent.ConnectionFailed(clock.instant(), Side.SOURCE, Optional.empty(),
                        Optional.of(listeningOn), FailureStage.ACCEPT, Sockets.describe(e)));
                pauseAfterAcceptFailure();
                continue;
            }
            ConnectionId id = new ConnectionId(nextId.incrementAndGet());
            RelayedConnection connection = new RelayedConnection(id, client, destination.get(), configuration, sink,
                    gate, clock, threads, () -> connections.remove(id));
            connections.put(id, connection);
            try {
                threads.execute(connection::run);
            } catch (RejectedExecutionException e) {
                connections.remove(id);
                closeQuietly(client);
                return;
            }
        }
    }

    /** Avoids a hot loop when accept keeps failing, e.g. when the process runs out of file descriptors. */
    private static void pauseAfterAcceptFailure() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public Endpoint listeningOn() {
        return listeningOn;
    }

    @Override
    public void changeDestination(Endpoint newDestination) {
        destination.set(newDestination);
    }

    @Override
    public void send(ConnectionId connection, Side side, Payload payload) throws IOException {
        RelayedConnection target = connections.get(connection);
        if (target == null) {
            throw new UnknownConnectionException(connection);
        }
        target.send(side, payload);
    }

    @Override
    public void disconnect(ConnectionId connection) {
        RelayedConnection target = connections.get(connection);
        if (target != null) {
            target.terminate("disconnected by user");
        }
    }

    @Override
    public Set<ConnectionId> openConnections() {
        return Set.copyOf(connections.keySet());
    }

    /** Stops accepting, closes every connection and waits briefly for their final events. */
    @Override
    public void close() {
        if (closing) {
            return;
        }
        closing = true;
        closeQuietly(server);
        connections.values().forEach(connection -> connection.terminate("proxy stopped"));
        threads.shutdown();
        try {
            if (!threads.awaitTermination(SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                LOG.log(System.Logger.Level.WARNING, "Relay threads did not stop in time");
                threads.shutdownNow();
            }
        } catch (InterruptedException e) {
            threads.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.DEBUG, "close failed", e);
        }
    }
}
