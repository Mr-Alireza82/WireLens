package io.github.mralireza82.wirelens.domain.traffic;

/**
 * Identifies one relayed connection. Both legs of the relay (client side and destination side) share it, so the
 * user can follow a single conversation across both panels.
 */
public record ConnectionId(long value) implements Comparable<ConnectionId> {
    public ConnectionId {
        if (value < 1) {
            throw new IllegalArgumentException("connection ids start at 1");
        }
    }

    @Override
    public int compareTo(ConnectionId other) {
        return Long.compare(value, other.value);
    }

    /** {@code #42}. */
    @Override
    public String toString() {
        return "#" + value;
    }
}
