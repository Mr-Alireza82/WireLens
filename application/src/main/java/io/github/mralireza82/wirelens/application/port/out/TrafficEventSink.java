package io.github.mralireza82.wirelens.application.port.out;

import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;

/**
 * Receives traffic events (Observer). Called from network threads, so implementations must be thread-safe and must
 * return quickly: never block, never touch a UI toolkit directly.
 */
@FunctionalInterface
public interface TrafficEventSink {
    void publish(TrafficEvent event);
}
