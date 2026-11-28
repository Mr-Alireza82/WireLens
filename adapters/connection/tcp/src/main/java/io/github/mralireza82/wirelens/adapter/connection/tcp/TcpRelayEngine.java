package io.github.mralireza82.wirelens.adapter.connection.tcp;

import io.github.mralireza82.wirelens.relay.application.port.out.RelayEngine;
import io.github.mralireza82.wirelens.relay.application.port.out.RunningRelay;
import io.github.mralireza82.wirelens.relay.application.port.out.TrafficGate;
import io.github.mralireza82.wirelens.relay.application.port.out.TrafficEventSink;
import io.github.mralireza82.wirelens.relay.domain.proxy.ProxyConfiguration;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;

/**
 * Starts TCP relays.
 *
 * <p>Design: one thread accepts clients; each connection then uses two threads, one pumping client-to-destination
 * and one destination-to-client, with plain blocking streams. The thread-per-direction model keeps the code linear
 * and easy to reason about, without selector loops or callback chains. See {@link RelayThreads} for which kind of
 * thread is used.
 */
public final class TcpRelayEngine implements RelayEngine {
    private static final int BACKLOG = 256;

    /** System property that selects {@link RelayThreads#VIRTUAL} ({@code -Dwirelens.relay.threads=virtual}). */
    public static final String THREADS_PROPERTY = "wirelens.relay.threads";

    /**
     * Which threads run the accept loop and the two pumps of every connection.
     *
     * <p>{@link #PLATFORM} is the default: on Windows with JDK 26 a virtual thread blocked writing to a loopback
     * socket was seen to miss its wake-up (both ends waiting, data stuck) under multi-megabyte bursts. Platform
     * threads use the OS's blocking calls directly and cannot hit that. They cost more memory per connection,
     * which is fine for an inspection proxy (hundreds of connections, not hundreds of thousands).
     */
    public enum RelayThreads {
        PLATFORM,
        VIRTUAL;

        ThreadFactory factory() {
            return this == VIRTUAL ? Thread.ofVirtual().name("wirelens-relay-", 0).factory()
                    : Thread.ofPlatform().daemon().stackSize(256 * 1024).name("wirelens-relay-", 0).factory();
        }

        /** {@code -Dwirelens.relay.threads=virtual} switches back to virtual threads; anything else is platform. */
        public static RelayThreads fromSystemProperty() {
            return "virtual".equalsIgnoreCase(System.getProperty(THREADS_PROPERTY, "")) ? VIRTUAL : PLATFORM;
        }
    }

    private final Clock clock;
    private final TrafficGate gate;
    private final RelayThreads threads;

    /** An engine that forwards everything immediately. */
    public TcpRelayEngine(Clock clock) {
        this(clock, TrafficGate.OPEN);
    }

    /** @param gate consulted before each received chunk is forwarded (hold mode) */
    public TcpRelayEngine(Clock clock, TrafficGate gate) {
        this(clock, gate, RelayThreads.fromSystemProperty());
    }

    public TcpRelayEngine(Clock clock, TrafficGate gate, RelayThreads threads) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.threads = Objects.requireNonNull(threads, "threads");
    }

    @Override
    public RunningRelay start(ProxyConfiguration configuration, TrafficEventSink sink) throws IOException {
        ServerSocket server = new ServerSocket();
        try {
            server.setReuseAddress(true);
            server.bind(Sockets.toSocketAddress(configuration.listen()), BACKLOG);
        } catch (IOException | RuntimeException e) {
            server.close();
            throw e;
        }
        TcpRelay relay = new TcpRelay(server, configuration, sink, gate, clock, threads.factory());
        relay.startAccepting();
        return relay;
    }
}
