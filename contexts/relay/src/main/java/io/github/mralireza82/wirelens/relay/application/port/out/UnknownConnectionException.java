package io.github.mralireza82.wirelens.relay.application.port.out;

import io.github.mralireza82.wirelens.kernel.id.ConnectionId;

import java.io.IOException;

/** The connection does not exist or was already closed. */
public final class UnknownConnectionException extends IOException {
    private static final long serialVersionUID = 1L;

    public UnknownConnectionException(ConnectionId id) {
        super("Connection " + id + " is not open");
    }
}
