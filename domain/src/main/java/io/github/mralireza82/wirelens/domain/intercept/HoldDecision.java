package io.github.mralireza82.wirelens.domain.intercept;

import io.github.mralireza82.wirelens.domain.traffic.Payload;

import java.util.Objects;

/** What happens to bytes that reached the relay: passed on (possibly changed) or discarded. */
public sealed interface HoldDecision {
    /** Passes the bytes on unchanged; used for every message while holding is off. */
    static HoldDecision pass(Payload payload) {
        return new Forward(payload, false);
    }

    /**
     * Passes bytes on to the other side.
     *
     * @param edited whether the user changed the bytes before they were forwarded
     */
    record Forward(Payload payload, boolean edited) implements HoldDecision {
        public Forward {
            Objects.requireNonNull(payload, "payload");
        }
    }

    /** Discards the bytes: the other side never sees them. */
    record Drop() implements HoldDecision {
    }
}
