package io.github.mralireza82.wirelens.app.windows;

import io.github.mralireza82.wirelens.relay.application.port.in.ProxyControl;
import io.github.mralireza82.wirelens.presentation.traffic.settings.SettingsRepository;
import io.github.mralireza82.wirelens.inspection.application.render.Radix;
import io.github.mralireza82.wirelens.inspection.application.render.RenderSettings;
import io.github.mralireza82.wirelens.inspection.application.render.TextEncoding;
import io.github.mralireza82.wirelens.presentation.traffic.settings.UserSettings;
import io.github.mralireza82.wirelens.kernel.net.Endpoint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Demo mode: a tiny payment-host simulator plus three clients, all on loopback, so WireLens shows real traffic
 * immediately after start-up. Nothing here is used outside {@code --demo}.
 *
 * <ul>
 *   <li>A "POS terminal" sends length-prefixed ISO 8583-style echo tests (0800) and gets 0810 answers.</li>
 *   <li>A "monitor" speaks a line-based text protocol (PING / PONG).</li>
 *   <li>A short-lived client connects, sends random binary bytes, half-closes and disconnects.</li>
 * </ul>
 */
final class DemoTraffic implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(DemoTraffic.class.getName());

    private final ServerSocket host;
    private final int listenPort;
    private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean closed;

    private DemoTraffic(ServerSocket host, int listenPort) {
        this.host = host;
        this.listenPort = listenPort;
    }

    static DemoTraffic prepare() {
        try {
            ServerSocket host = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            int listenPort;
            try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                listenPort = probe.getLocalPort();
            }
            DemoTraffic demo = new DemoTraffic(host, listenPort);
            demo.threads.execute(demo::acceptHostConnections);
            return demo;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start the demo host", e);
        }
    }

    /** Settings that point the UI at the simulator; nothing is persisted in demo mode. */
    SettingsRepository settings() {
        UserSettings demo = new UserSettings("127.0.0.1", Integer.toString(listenPort), "127.0.0.1",
                Integer.toString(host.getLocalPort()), RenderSettings.DEFAULT,
                new RenderSettings(TextEncoding.ASCII, Radix.HEXADECIMAL, " ", 16), true, 50_000);
        return new SettingsRepository() {
            private UserSettings current = demo;

            @Override
            public UserSettings load() {
                return current;
            }

            @Override
            public void save(UserSettings settings) {
                current = settings;
            }
        };
    }

    /** Starts the relay and the demo clients; called once the window is up. */
    void runAfterStartup(ProxyControl proxy) {
        runAfterStartup(proxy, false);
    }

    /** With {@code stress}, four extra clients also send as fast as the relay allows (load testing). */
    void runAfterStartup(ProxyControl proxy, boolean stress) {
        Thread.ofPlatform().daemon().name("wirelens-demo").start(() -> {
            proxy.start(Endpoint.of("127.0.0.1", listenPort), Endpoint.of("127.0.0.1", host.getLocalPort()));
            threads.execute(this::posTerminal);
            threads.execute(this::lineClient);
            threads.execute(this::shortLivedClients);
            if (stress) {
                for (int i = 0; i < 4; i++) {
                    threads.execute(this::floodClient);
                }
            }
        });
    }

    // ------------------------------------------------------------------ simulated host

    private void acceptHostConnections() {
        while (!closed) {
            try {
                Socket socket = host.accept();
                threads.execute(() -> serve(socket));
            } catch (IOException e) {
                return;
            }
        }
    }

    private static void serve(Socket socket) {
        try (socket; InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) >= 0) {
                if (count >= 6 && buffer[0] == 0 && (buffer[1] & 0xFF) == count - 2) {
                    String request = new String(buffer, 2, count - 2, StandardCharsets.US_ASCII);
                    out.write(frame("0810" + request.substring(4, Math.min(request.length(), 36)) + "00"));
                } else if (buffer[0] >= 'A' && buffer[0] <= 'Z') {
                    String line = new String(buffer, 0, count, StandardCharsets.US_ASCII).strip();
                    out.write((line.startsWith("PING") ? "PONG" + line.substring(4) : "OK " + line)
                            .concat("\r\n").getBytes(StandardCharsets.US_ASCII));
                } else {
                    out.write(new byte[] {0x06}); // ACK
                }
            }
        } catch (IOException ignored) {
            // demo peer went away
        }
    }

    // ------------------------------------------------------------------ simulated clients

    private void posTerminal() {
        int stan = 1;
        while (!closed) {
            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), listenPort)) {
                InputStream in = socket.getInputStream();
                for (int i = 0; i < 20 && !closed; i++) {
                    String message = "0800" + "8220000000000000" + "0400000000000000"
                            + "1124" + String.format("%06d", stan++) + "301";
                    socket.getOutputStream().write(frame(message));
                    in.readNBytes(2 + 4 + 32 + 2);
                    pause(2000);
                }
            } catch (IOException e) {
                pause(2000);
            }
        }
    }

    private void lineClient() {
        int sequence = 1;
        while (!closed) {
            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), listenPort)) {
                for (int i = 0; i < 10 && !closed; i++) {
                    socket.getOutputStream().write(("PING " + sequence++ + "\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getInputStream().read(new byte[64]);
                    pause(3000);
                }
            } catch (IOException e) {
                pause(3000);
            }
        }
    }

    private void shortLivedClients() {
        Random random = new Random();
        while (!closed) {
            pause(5000);
            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), listenPort)) {
                byte[] noise = new byte[16 + random.nextInt(48)];
                random.nextBytes(noise);
                noise[0] = (byte) 0xF0;
                socket.getOutputStream().write(noise);
                socket.shutdownOutput();
                socket.getInputStream().readAllBytes();
            } catch (IOException e) {
                LOG.log(System.Logger.Level.DEBUG, "demo client failed", e);
            }
        }
    }

    /** Sends 512-byte messages back to back and drains the echoes. */
    private void floodClient() {
        byte[] message = new byte[512];
        new Random().nextBytes(message);
        message[0] = (byte) 0xF0;
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), listenPort)) {
            threads.execute(() -> {
                try {
                    socket.getInputStream().transferTo(OutputStream.nullOutputStream());
                } catch (IOException ignored) {
                    // closed
                }
            });
            OutputStream out = socket.getOutputStream();
            while (!closed) {
                out.write(message);
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.DEBUG, "flood client stopped", e);
        }
    }

    private static byte[] frame(String message) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] body = message.getBytes(StandardCharsets.US_ASCII);
        bytes.write(body.length >> 8);
        bytes.write(body.length);
        bytes.writeBytes(body);
        return bytes.toByteArray();
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            host.close();
        } catch (IOException ignored) {
            // shutting down
        }
        threads.shutdownNow();
    }
}
