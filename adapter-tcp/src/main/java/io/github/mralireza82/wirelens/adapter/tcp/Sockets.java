package io.github.mralireza82.wirelens.adapter.tcp;

import io.github.mralireza82.wirelens.domain.net.Endpoint;
import io.github.mralireza82.wirelens.domain.net.IpAddress;
import io.github.mralireza82.wirelens.domain.net.Port;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

/** Converts between domain endpoints and {@code java.net} addresses. Never performs DNS lookups. */
final class Sockets {
    private Sockets() {
    }

    static InetSocketAddress toSocketAddress(Endpoint endpoint) {
        try {
            return new InetSocketAddress(InetAddress.getByAddress(endpoint.address().octets()), endpoint.port().value());
        } catch (UnknownHostException e) {
            throw new IllegalStateException("4 or 16 address bytes are always valid", e);
        }
    }

    static Endpoint toEndpoint(SocketAddress address) {
        InetSocketAddress inet = (InetSocketAddress) address;
        return new Endpoint(IpAddress.ofOctets(inet.getAddress().getAddress()), Port.of(inet.getPort()));
    }

    /** A short, user-friendly description of an I/O failure. */
    static String describe(Throwable error) {
        String message = error.getMessage();
        String type = error.getClass().getSimpleName();
        if (message == null || message.isBlank()) {
            return type;
        }
        return switch (type) {
            case "ConnectException", "SocketException", "IOException" -> message;
            case "SocketTimeoutException" -> "Timed out (" + message + ")";
            default -> type + ": " + message;
        };
    }
}
