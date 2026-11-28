package io.github.mralireza82.wirelens.relay.domain.traffic;

import io.github.mralireza82.wirelens.kernel.bytes.Payload;
import io.github.mralireza82.wirelens.kernel.id.ConnectionId;

import io.github.mralireza82.wirelens.kernel.net.Endpoint;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Something that happened on the relay. A closed (sealed) hierarchy, so every consumer can switch over all
 * cases exhaustively and the compiler reports any case a new event type forgets.
 */
public sealed interface TrafficEvent {

    Instant timestamp();

    Side side();

    EventKind kind();

    /** The connection this event belongs to, if any. */
    Optional<ConnectionId> connectionId();

    /** The peer of this leg (client or destination), if known. */
    Optional<Endpoint> remote();

    /** Bytes carried by this event; zero for everything except data. */
    default int byteCount() {
        return 0;
    }

    /** Events that belong to an established leg. */
    sealed interface OnLink extends TrafficEvent {
        Link link();

        @Override
        default Side side() {
            return link().side();
        }

        @Override
        default Optional<ConnectionId> connectionId() {
            return Optional.of(link().id());
        }

        @Override
        default Optional<Endpoint> remote() {
            return Optional.of(link().remote());
        }
    }

    /** A leg was established: a client was accepted, or the proxy connected to the destination. */
    record ConnectionOpened(Instant timestamp, Link link) implements OnLink {
        public ConnectionOpened {
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(link, "link");
        }

        @Override
        public EventKind kind() {
            return EventKind.OPENED;
        }
    }

    /** Bytes were received from, or sent to, the peer of a leg. */
    record DataTransferred(Instant timestamp, Link link, Direction direction, Payload payload, DataOrigin origin)
            implements OnLink {
        public DataTransferred {
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(link, "link");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(origin, "origin");
        }

        @Override
        public EventKind kind() {
            return EventKind.DATA;
        }

        @Override
        public int byteCount() {
            return payload.size();
        }
    }

    /**
     * One direction of a leg finished (TCP half-close): the peer sent FIN ({@link Direction#INBOUND}) or the proxy
     * passed a FIN on to the peer ({@link Direction#OUTBOUND}). The other direction may still carry data.
     */
    record StreamEnded(Instant timestamp, Link link, Direction direction) implements OnLink {
        public StreamEnded {
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(link, "link");
            Objects.requireNonNull(direction, "direction");
        }

        @Override
        public EventKind kind() {
            return EventKind.STREAM_ENDED;
        }
    }

    /** A leg was closed, with its totals. */
    record ConnectionClosed(Instant timestamp, Link link, long bytesIn, long bytesOut, Duration duration,
                            String reason) implements OnLink {
        public ConnectionClosed {
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(link, "link");
            Objects.requireNonNull(duration, "duration");
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public EventKind kind() {
            return EventKind.CLOSED;
        }
    }

    /** Something went wrong: binding, accepting, connecting to the destination, reading or writing. */
    record ConnectionFailed(Instant timestamp, Side side, Optional<ConnectionId> connectionId,
                            Optional<Endpoint> remote, FailureStage stage, String message) implements TrafficEvent {
        public ConnectionFailed {
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(connectionId, "connectionId");
            Objects.requireNonNull(remote, "remote");
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(message, "message");
        }

        @Override
        public EventKind kind() {
            return EventKind.FAILED;
        }
    }

    /** A message about the proxy itself, e.g. "Listening on 0.0.0.0:9000" or "Destination changed". */
    record Notice(Instant timestamp, Side side, String message) implements TrafficEvent {
        public Notice {
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(message, "message");
        }

        @Override
        public EventKind kind() {
            return EventKind.NOTICE;
        }

        @Override
        public Optional<ConnectionId> connectionId() {
            return Optional.empty();
        }

        @Override
        public Optional<Endpoint> remote() {
            return Optional.empty();
        }
    }
}
