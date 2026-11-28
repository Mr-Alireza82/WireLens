package io.github.mralireza82.wirelens.relay.application.port.out;

import io.github.mralireza82.wirelens.kernel.net.Endpoint;
import io.github.mralireza82.wirelens.kernel.id.ConnectionId;
import io.github.mralireza82.wirelens.kernel.bytes.Payload;
import io.github.mralireza82.wirelens.relay.domain.traffic.Side;

import java.io.IOException;
import java.util.Set;

/** A started relay. Closing it stops listening and closes every open connection. */
public interface RunningRelay extends AutoCloseable {

    /** The address actually bound (useful when port 0 was requested). */
    Endpoint listeningOn();

    /** New clients are forwarded here; connections already open keep their destination. */
    void changeDestination(Endpoint destination);

    /**
     * Sends bytes typed in by the user to one leg of an open connection: {@link Side#SOURCE} sends to the client,
     * {@link Side#DESTINATION} to the destination.
     *
     * @throws UnknownConnectionException when the connection is not open (any more)
     * @throws IOException                when writing fails
     */
    void send(ConnectionId connection, Side side, Payload payload) throws IOException;

    /** Closes both legs of one connection; does nothing if it is already closed. */
    void disconnect(ConnectionId connection);

    /** Connections whose legs are still open. */
    Set<ConnectionId> openConnections();

    @Override
    void close();
}
