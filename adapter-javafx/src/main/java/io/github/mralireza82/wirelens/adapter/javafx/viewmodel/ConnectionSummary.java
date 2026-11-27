package io.github.mralireza82.wirelens.adapter.javafx.viewmodel;

import io.github.mralireza82.wirelens.domain.net.Endpoint;
import io.github.mralireza82.wirelens.domain.traffic.ConnectionId;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * What the UI knows about one relayed connection: client, the proxy address it reached, and the destination it is
 * forwarded to (known once the proxy connected upstream). Immutable; a new value replaces the old one when the
 * destination leg opens or the connection closes.
 *
 * @param proxy the proxy's listening end the client connected to (what the POS must dial)
 */
public record ConnectionSummary(ConnectionId id, Endpoint client, Endpoint proxy, Optional<Endpoint> destination,
                                Instant openedAt, boolean open) {
    public ConnectionSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(proxy, "proxy");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(openedAt, "openedAt");
    }

    public ConnectionSummary withDestination(Endpoint endpoint) {
        return new ConnectionSummary(id, client, proxy, Optional.of(endpoint), openedAt, open);
    }

    public ConnectionSummary closed() {
        return new ConnectionSummary(id, client, proxy, destination, openedAt, false);
    }

    /** {@code client → proxy → destination}, the way traffic travels. */
    public String route() {
        return client + "  →  " + proxy + "  →  " + destination.map(Object::toString).orElse("connecting…");
    }

    /** Case-insensitive match on the id ({@code #3} or {@code 3}) and every address in the route. */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.strip().toLowerCase(Locale.ROOT);
        String haystack = (id + " " + id.value() + " " + route()).toLowerCase(Locale.ROOT);
        for (String part : needle.split("\\s+")) {
            if (!haystack.contains(part)) {
                return false;
            }
        }
        return true;
    }
}
