package io.github.mralireza82.wirelens.relay.application.port.in;

import io.github.mralireza82.wirelens.relay.domain.intercept.HeldMessage;
import io.github.mralireza82.wirelens.relay.domain.intercept.HoldId;
import io.github.mralireza82.wirelens.kernel.net.Endpoint;
import io.github.mralireza82.wirelens.kernel.bytes.Payload;
import io.github.mralireza82.wirelens.relay.domain.traffic.Side;

import java.io.IOException;
import java.util.List;

/**
 * Use case: hold mode. While holding is on for a side, every message received on that side waits until the user
 * forwards it (as is or edited), drops it, or sends a copy elsewhere.
 */
public interface HoldCommands {
    /** Turns holding on or off for messages received on {@code receivedOn}. Turning it off forwards what waits. */
    void setHolding(Side receivedOn, boolean holding);

    boolean isHolding(Side receivedOn);

    /** Messages currently waiting, oldest first. */
    List<HeldMessage> held();

    /** Called (on any thread) whenever holding is switched or the set of waiting messages changes. */
    void onChange(Runnable listener);

    /** @return false if the message is no longer waiting (already decided, or its connection closed) */
    boolean forward(HoldId id);

    /** Forwards different bytes in place of the held ones. */
    boolean forwardEdited(HoldId id, Payload replacement);

    boolean drop(HoldId id);

    /** @return how many messages were forwarded */
    int forwardAll(Side receivedOn);

    /** @return how many messages were dropped */
    int dropAll(Side receivedOn);

    /**
     * Sends a copy of a held message (or of edited bytes) to another address over a new, short-lived connection
     * and returns the reply. The held message itself stays held.
     */
    Payload sendCopy(HoldId id, Payload bytes, Endpoint target) throws IOException;
}
