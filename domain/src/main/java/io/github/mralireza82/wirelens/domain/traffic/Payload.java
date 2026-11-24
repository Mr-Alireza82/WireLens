package io.github.mralireza82.wirelens.domain.traffic;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * The raw bytes of one data event, exactly as received or sent (value object).
 *
 * <p>Raw bytes are the single source of truth in WireLens: every text, number or hex-dump view is derived from
 * them on demand and nothing ever writes back. The array is copied in and never handed out, so a payload can be
 * shared freely between threads.
 */
public final class Payload {
    private static final Payload EMPTY = new Payload(new byte[0]);

    private final byte[] bytes;

    private Payload(byte[] bytes) {
        this.bytes = bytes;
    }

    public static Payload copyOf(byte[] source) {
        Objects.requireNonNull(source, "source");
        return source.length == 0 ? EMPTY : new Payload(source.clone());
    }

    public static Payload copyOf(byte[] source, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, Objects.requireNonNull(source, "source").length);
        return length == 0 ? EMPTY : new Payload(Arrays.copyOfRange(source, offset, offset + length));
    }

    public static Payload empty() {
        return EMPTY;
    }

    public int size() {
        return bytes.length;
    }

    public boolean isEmpty() {
        return bytes.length == 0;
    }

    /** The byte at {@code index} as 0..255. */
    public int unsignedAt(int index) {
        return bytes[index] & 0xFF;
    }

    /** A fresh copy of all bytes. */
    public byte[] toByteArray() {
        return bytes.clone();
    }

    /** A fresh copy of at most {@code maxLength} leading bytes. */
    public byte[] head(int maxLength) {
        return Arrays.copyOf(bytes, Math.min(Math.max(maxLength, 0), bytes.length));
    }

    /** A read-only view without copying, for writing to channels. */
    public ByteBuffer asReadOnlyBuffer() {
        return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Payload payload && Arrays.equals(bytes, payload.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "Payload[" + bytes.length + " bytes]";
    }
}
