package io.github.mralireza82.wirelens.adapter.tcp;

import io.github.mralireza82.wirelens.application.port.out.OneShotSender;
import io.github.mralireza82.wirelens.domain.net.Endpoint;
import io.github.mralireza82.wirelens.domain.traffic.Payload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;

/**
 * Sends bytes over a fresh TCP connection and collects the reply until the peer closes, the reply stays quiet for
 * the timeout, or {@link #MAX_REPLY_BYTES} arrived. The connection keeps its sending side open while reading,
 * because many payment hosts close the connection as soon as they see the client's FIN.
 */
public final class TcpOneShotSender implements OneShotSender {
    public static final int MAX_REPLY_BYTES = 1024 * 1024;
    private final Duration connectTimeout;

    public TcpOneShotSender(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    @Override
    public Payload exchange(Endpoint target, Payload payload, Duration replyTimeout) throws IOException {
        try (Socket socket = new Socket()) {
            socket.setTcpNoDelay(true);
            socket.connect(Sockets.toSocketAddress(target), (int) connectTimeout.toMillis());
            socket.getOutputStream().write(payload.toByteArray());
            socket.getOutputStream().flush();
            socket.setSoTimeout((int) Math.max(1, replyTimeout.toMillis()));
            ByteArrayOutputStream reply = new ByteArrayOutputStream();
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[8192];
            try {
                int count;
                while (reply.size() < MAX_REPLY_BYTES && (count = in.read(buffer)) > 0) {
                    reply.write(buffer, 0, Math.min(count, MAX_REPLY_BYTES - reply.size()));
                }
            } catch (SocketTimeoutException quiet) {
                // the peer stopped talking but kept the connection open: what arrived so far is the reply
            }
            return Payload.copyOf(reply.toByteArray());
        }
    }
}
