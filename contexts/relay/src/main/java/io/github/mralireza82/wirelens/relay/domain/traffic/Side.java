package io.github.mralireza82.wirelens.relay.domain.traffic;

/** Which leg of the relay an event belongs to. */
public enum Side {
    /** The leg between a connecting client (the source) and the proxy's listening socket. */
    SOURCE,
    /** The leg between the proxy and the configured destination. */
    DESTINATION;

    public Side opposite() {
        return this == SOURCE ? DESTINATION : SOURCE;
    }
}
