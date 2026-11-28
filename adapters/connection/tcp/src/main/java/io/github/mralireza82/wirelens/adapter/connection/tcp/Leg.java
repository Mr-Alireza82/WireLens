package io.github.mralireza82.wirelens.adapter.connection.tcp;

import io.github.mralireza82.wirelens.relay.domain.traffic.DataOrigin;
import io.github.mralireza82.wirelens.relay.domain.traffic.Direction;
import io.github.mralireza82.wirelens.relay.domain.traffic.Link;
import io.github.mralireza82.wirelens.kernel.bytes.Payload;
import io.github.mralireza82.wirelens.relay.domain.traffic.TrafficEvent;
import io.github.mralireza82.wirelens.relay.application.port.out.TrafficEventSink;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One socket of a relayed connection plus its counters. Writes are serialised with a lock because relayed bytes
 * and bytes typed in by the user can arrive at the same time; a {@link ReentrantLock} (not {@code synchronized})
 * keeps virtual threads from pinning their carrier while they block on the socket.
 */
final class Leg {
    private final Socket socket;
    private final Link link;
    private final Instant openedAt;
    private final InputStream in;
    private final OutputStream out;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final AtomicLong bytesIn = new AtomicLong();
    private final AtomicLong bytesOut = new AtomicLong();

    Leg(Socket socket, Link link, Instant openedAt) throws IOException {
        this.socket = socket;
        this.link = link;
        this.openedAt = openedAt;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    Link link() {
        return link;
    }

    /** Blocks until bytes arrive; -1 at end of stream. */
    int read(byte[] buffer) throws IOException {
        int count = in.read(buffer);
        if (count > 0) {
            bytesIn.addAndGet(count);
        }
        return count;
    }

    /** Writes the bytes, then reports them as sent. */
    void write(Payload payload, DataOrigin origin, TrafficEventSink sink, Clock clock) throws IOException {
        writeLock.lock();
        try {
            out.write(payload.toByteArray());
            out.flush();
            bytesOut.addAndGet(payload.size());
            sink.publish(new TrafficEvent.DataTransferred(clock.instant(), link, Direction.OUTBOUND, payload, origin));
        } finally {
            writeLock.unlock();
        }
    }

    /** Passes an end-of-stream on: the peer reads EOF but can keep sending (TCP half-close). */
    void shutdownOutput() throws IOException {
        writeLock.lock();
        try {
            if (!socket.isClosed() && !socket.isOutputShutdown()) {
                socket.shutdownOutput();
            }
        } finally {
            writeLock.unlock();
        }
    }

    void close() {
        TcpRelay.closeQuietly(socket);
    }

    TrafficEvent.ConnectionClosed closedEvent(Instant now, String reason) {
        return new TrafficEvent.ConnectionClosed(now, link, bytesIn.get(), bytesOut.get(),
                Duration.between(openedAt, now), reason);
    }
}
