package io.github.mralireza82.wirelens.application.port.in;

import io.github.mralireza82.wirelens.domain.traffic.ConnectionId;
import io.github.mralireza82.wirelens.domain.traffic.Payload;
import io.github.mralireza82.wirelens.domain.traffic.Side;

import java.io.IOException;
import java.util.Set;

/** Use case: act on individual connections (send crafted bytes, disconnect). */
public interface ConnectionCommands {

    /** Sends bytes to the client ({@link Side#SOURCE}) or to the destination ({@link Side#DESTINATION}). */
    void send(ConnectionId connection, Side side, Payload payload) throws IOException;

    void disconnect(ConnectionId connection);

    Set<ConnectionId> openConnections();
}
