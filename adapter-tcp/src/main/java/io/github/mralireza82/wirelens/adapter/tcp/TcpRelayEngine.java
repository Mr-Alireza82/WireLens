package io.github.mralireza82.wirelens.adapter.tcp;

import io.github.mralireza82.wirelens.application.port.out.RelayEngine;
import io.github.mralireza82.wirelens.application.port.out.RunningRelay;
import io.github.mralireza82.wirelens.application.port.out.TrafficEventSink;
import io.github.mralireza82.wirelens.domain.proxy.ProxyConfiguration;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Clock;
import java.util.Objects;

/**
 * Starts TCP relays.
 *
 * <p>Design: one virtual thread accepts clients; each connection then uses two virtual threads, one pumping
 * client-to-destination and one destination-to-client, with plain blocking streams. Virtual threads make this
 * thread-per-direction model cheap enough for thousands of connections while keeping the code linear and easy to
 * reason about, without selector loops or callback chains.
 */
public final class TcpRelayEngine implements RelayEngine {
    private static final int BACKLOG = 256;

    private final Clock clock;

    public TcpRelayEngine(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
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
        TcpRelay relay = new TcpRelay(server, configuration, sink, clock);
        relay.startAccepting();
        return relay;
    }
}
