package io.github.mralireza82.wirelens.relay.domain.intercept;

/** Identifies one held message for as long as it waits; printed as {@code H12}. */
public record HoldId(long value) {
    public HoldId {
        if (value <= 0) {
            throw new IllegalArgumentException("hold id must be positive: " + value);
        }
    }

    @Override
    public String toString() {
        return "H" + value;
    }
}
