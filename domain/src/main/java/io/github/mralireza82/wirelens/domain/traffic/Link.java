package io.github.mralireza82.wirelens.domain.traffic;

import io.github.mralireza82.wirelens.domain.net.Endpoint;

import java.util.Objects;

/**
 * One leg of a relayed connection: which connection, which side, and the two socket ends.
 *
 * @param local  the proxy's own end of this leg
 * @param remote the peer: the client on the source side, the destination server on the destination side
 */
public record Link(ConnectionId id, Side side, Endpoint local, Endpoint remote) {
    public Link {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(remote, "remote");
    }
}
