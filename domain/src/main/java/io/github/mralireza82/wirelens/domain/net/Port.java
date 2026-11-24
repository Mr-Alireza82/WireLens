package io.github.mralireza82.wirelens.domain.net;

import java.util.Objects;
import java.util.Optional;

/** A TCP port number, 1 to 65535 (value object). Port 0 ("any free port") is only allowed for listening. */
public record Port(int value) implements Comparable<Port> {
    public static final int MAX = 65_535;

    public Port {
        if (value < 0 || value > MAX) {
            throw new InvalidAddressException("A port is a number from 1 to " + MAX + ", not " + value);
        }
    }

    public static Port of(int value) {
        return new Port(value);
    }

    /** Parses user input. Rejects 0, because a destination or a listening port chosen by a person must be real. */
    public static Port parse(String text) {
        Objects.requireNonNull(text, "text");
        String value = text.strip();
        if (value.isEmpty()) {
            throw new InvalidAddressException("Enter a port number");
        }
        if (value.length() > 5 || !value.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new InvalidAddressException("A port is a number from 1 to " + MAX);
        }
        int number = Integer.parseInt(value);
        if (number < 1 || number > MAX) {
            throw new InvalidAddressException("A port is a number from 1 to " + MAX + ", not " + number);
        }
        return new Port(number);
    }

    /** Returns an error message for invalid text, or empty when the text is a valid port. */
    public static Optional<String> validate(String text) {
        try {
            parse(text);
            return Optional.empty();
        } catch (InvalidAddressException e) {
            return Optional.of(e.getMessage());
        }
    }

    @Override
    public int compareTo(Port other) {
        return Integer.compare(value, other.value);
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
