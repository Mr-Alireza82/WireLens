package io.github.mralireza82.wirelens.relay.application.proxy;

import io.github.mralireza82.wirelens.kernel.net.Endpoint;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable snapshot of the relay's state, safe to hand to any thread.
 *
 * @param listen      the bound address while running, otherwise the last requested one (may be absent)
 * @param destination where new connections are forwarded (may be absent before the first start)
 * @param since       when the current state began
 * @param error       why the last start failed, for {@link ProxyState#FAILED}
 */
public record ProxyStatus(ProxyState state, Optional<Endpoint> listen, Optional<Endpoint> destination,
                          Instant since, Optional<String> error) {
    public ProxyStatus {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(listen, "listen");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(since, "since");
        Objects.requireNonNull(error, "error");
    }

    public static ProxyStatus stopped(Instant now) {
        return new ProxyStatus(ProxyState.STOPPED, Optional.empty(), Optional.empty(), now, Optional.empty());
    }
}
