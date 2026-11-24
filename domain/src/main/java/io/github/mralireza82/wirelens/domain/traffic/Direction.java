package io.github.mralireza82.wirelens.domain.traffic;

/** Direction of bytes on one leg, always seen from the proxy. */
public enum Direction {
    /** The peer sent bytes and the proxy received them. */
    INBOUND,
    /** The proxy sent bytes to the peer. */
    OUTBOUND
}
