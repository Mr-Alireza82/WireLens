package io.github.mralireza82.wirelens.application.port.out;

import io.github.mralireza82.wirelens.domain.intercept.HoldDecision;
import io.github.mralireza82.wirelens.domain.traffic.ConnectionId;
import io.github.mralireza82.wirelens.domain.traffic.Link;
import io.github.mralireza82.wirelens.domain.traffic.Payload;

/**
 * Asked by the relay before it forwards bytes it received. Implemented by the application (hold mode), called by
 * the networking adapter, so the adapter never needs to know about holding, editing or the UI.
 *
 * <p>{@link #admit} may block until the user decides; the relay calls it on the connection's own (virtual) thread
 * for that direction, so a held message delays only later bytes of the same connection and direction.
 */
public interface TrafficGate {
    /** Forwards everything immediately. */
    TrafficGate OPEN = new TrafficGate() {
        @Override
        public HoldDecision admit(Link from, Payload payload) {
            return HoldDecision.pass(payload);
        }

        @Override
        public void release(ConnectionId connection) {
        }
    };

    /**
     * Decides what to do with bytes received on {@code from}.
     *
     * @throws InterruptedException if the waiting thread is interrupted (the relay then stops the direction)
     */
    HoldDecision admit(Link from, Payload payload) throws InterruptedException;

    /** The connection is ending: anything still held for it is discarded so the waiting relay thread can finish. */
    void release(ConnectionId connection);
}
