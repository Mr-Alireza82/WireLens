package io.github.mralireza82.wirelens.domain.intercept;

import io.github.mralireza82.wirelens.domain.traffic.ConnectionId;
import io.github.mralireza82.wirelens.domain.traffic.Link;
import io.github.mralireza82.wirelens.domain.traffic.Payload;
import io.github.mralireza82.wirelens.domain.traffic.Side;

import java.time.Instant;
import java.util.Objects;

/**
 * Bytes that arrived on one leg and are waiting for the user's decision instead of being forwarded at once.
 *
 * @param from the leg the bytes were received on; they would travel on to the opposite side
 */
public record HeldMessage(HoldId id, Instant heldAt, Link from, Payload payload) {
    public HeldMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(heldAt, "heldAt");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(payload, "payload");
    }

    public ConnectionId connection() {
        return from.id();
    }

    /** The side the message was received on (source = from the client). */
    public Side receivedOn() {
        return from.side();
    }

    /** The side the message is on its way to. */
    public Side headingTo() {
        return receivedOn().opposite();
    }
}
