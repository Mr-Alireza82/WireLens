package io.github.mralireza82.wirelens.adapter.tcp;

import io.github.mralireza82.wirelens.domain.net.Endpoint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

/** Small destination servers for integration tests. */
final class TestServers {
    private TestServers() {
    }

    /** Replies to every chunk with {@code transform(chunk)}; passes a half-close back and closes at EOF. */
    static final class TransformServer implements AutoCloseable {
        private final ServerSocket server;
        final List<Socket> accepted = new CopyOnWriteArrayList<>();

        TransformServer(String host, UnaryOperator<byte[]> transform) throws IOException {
            server = new ServerSocket(0, 100, InetAddress.getByName(host));
            Thread.ofVirtual().start(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket socket = server.accept();
                        accepted.add(socket);
                        Thread.ofVirtual().start(() -> serve(socket, transform));
                    } catch (IOException e) {
                        return;
                    }
                }
            });
        }

        static TransformServer echo() throws IOException {
            return new TransformServer("127.0.0.1", bytes -> bytes);
        }

        private static void serve(Socket socket, UnaryOperator<byte[]> transform) {
            try (socket; InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) >= 0) {
                    out.write(transform.apply(java.util.Arrays.copyOf(buffer, count)));
                }
            } catch (IOException ignored) {
                // test peer went away
            }
        }

        Endpoint endpoint() {
            InetSocketAddress address = (InetSocketAddress) server.getLocalSocketAddress();
            return Sockets.toEndpoint(address);
        }

        @Override
        public void close() throws IOException {
            server.close();
            for (Socket socket : accepted) {
                socket.close();
            }
        }
    }

    /** Records everything it receives and never answers. */
    static final class SinkServer implements AutoCloseable {
        private final ServerSocket server;
        final ByteArrayOutputStream received = new ByteArrayOutputStream();

        SinkServer() throws IOException {
            server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
            Thread.ofVirtual().start(() -> {
                try (Socket socket = server.accept(); InputStream in = socket.getInputStream()) {
                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = in.read(buffer)) >= 0) {
                        synchronized (received) {
                            received.write(buffer, 0, count);
                        }
                    }
                } catch (IOException ignored) {
                    // closed by the test
                }
            });
        }

        byte[] received() {
            synchronized (received) {
                return received.toByteArray();
            }
        }

        Endpoint endpoint() {
            return Sockets.toEndpoint(server.getLocalSocketAddress());
        }

        @Override
        public void close() throws IOException {
            server.close();
        }
    }

    /** A port on which nothing listens. */
    static int closedPort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return probe.getLocalPort();
        }
    }
}
