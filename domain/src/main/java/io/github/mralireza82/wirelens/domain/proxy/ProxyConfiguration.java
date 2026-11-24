package io.github.mralireza82.wirelens.domain.proxy;

import io.github.mralireza82.wirelens.domain.net.Endpoint;

import java.time.Duration;
import java.util.Objects;

/**
 * What the relay needs to run.
 *
 * @param listen         where clients connect to
 * @param destination    where each client is forwarded to (can change while running; affects new connections)
 * @param bufferSize     maximum bytes read in one go, which is also the largest single data event
 * @param connectTimeout how long to wait for the destination to accept a connection
 */
public record ProxyConfiguration(Endpoint listen, Endpoint destination, int bufferSize, Duration connectTimeout) {
    public static final int DEFAULT_BUFFER_SIZE = 16 * 1024;
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    public ProxyConfiguration {
        Objects.requireNonNull(listen, "listen");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        if (bufferSize < 64 || bufferSize > 1 << 20) {
            throw new IllegalArgumentException("buffer size must be between 64 bytes and 1 MiB");
        }
        if (connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("connect timeout must be positive");
        }
    }

    public static ProxyConfiguration of(Endpoint listen, Endpoint destination) {
        return new ProxyConfiguration(listen, destination, DEFAULT_BUFFER_SIZE, DEFAULT_CONNECT_TIMEOUT);
    }

    public ProxyConfiguration withDestination(Endpoint newDestination) {
        return new ProxyConfiguration(listen, newDestination, bufferSize, connectTimeout);
    }
}
