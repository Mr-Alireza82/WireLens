package io.github.mralireza82.wirelens.relay.application.port.out;

import io.github.mralireza82.wirelens.kernel.net.Endpoint;
import io.github.mralireza82.wirelens.kernel.bytes.Payload;

import java.io.IOException;
import java.time.Duration;

/** Opens a short-lived connection, sends bytes and collects the reply. Implemented by the networking adapter. */
public interface OneShotSender {
    /**
     * Connects to {@code target}, writes {@code payload}, then reads until the peer closes or stays quiet for
     * {@code replyTimeout}.
     *
     * @return whatever the peer answered (possibly empty)
     * @throws IOException when connecting or writing fails
     */
    Payload exchange(Endpoint target, Payload payload, Duration replyTimeout) throws IOException;
}
