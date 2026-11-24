package io.github.mralireza82.wirelens.application.support;

import io.github.mralireza82.wirelens.domain.net.Endpoint;
import io.github.mralireza82.wirelens.domain.traffic.ConnectionId;
import io.github.mralireza82.wirelens.domain.traffic.DataOrigin;
import io.github.mralireza82.wirelens.domain.traffic.Direction;
import io.github.mralireza82.wirelens.domain.traffic.Link;
import io.github.mralireza82.wirelens.domain.traffic.Payload;
import io.github.mralireza82.wirelens.domain.traffic.Side;
import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;

import java.time.Duration;
import java.time.Instant;

/** Test data builders. */
public final class Events {
    public static final Instant T0 = Instant.parse("2026-11-24T10:15:30Z");

    private Events() {
    }

    public static Link link(long id, Side side) {
        return side == Side.SOURCE
                ? new Link(new ConnectionId(id), side, Endpoint.of("127.0.0.1", 9000), Endpoint.of("10.1.1.7", 50123))
                : new Link(new ConnectionId(id), side, Endpoint.of("127.0.0.1", 40000), Endpoint.of("10.9.9.9", 8583));
    }

    public static TrafficEvent.DataTransferred data(long id, Side side, Direction direction, byte... bytes) {
        return new TrafficEvent.DataTransferred(T0, link(id, side), direction, Payload.copyOf(bytes),
                DataOrigin.RELAYED);
    }

    public static TrafficEvent.ConnectionOpened opened(long id, Side side) {
        return new TrafficEvent.ConnectionOpened(T0, link(id, side));
    }

    public static TrafficEvent.ConnectionClosed closed(long id, Side side) {
        return new TrafficEvent.ConnectionClosed(T0, link(id, side), 0, 0, Duration.ofSeconds(1), "client closed");
    }
}
