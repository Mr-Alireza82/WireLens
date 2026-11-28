package io.github.mralireza82.wirelens.kernel.net;

/** Thrown when text is not a valid IP address, port or endpoint. The message is written for end users. */
public final class InvalidAddressException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public InvalidAddressException(String message) {
        super(message);
    }
}
