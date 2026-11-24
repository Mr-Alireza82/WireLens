package io.github.mralireza82.wirelens.domain.net;

import java.util.Objects;

/** An IP address and a port: one end of a TCP connection (value object). */
public record Endpoint(IpAddress address, Port port) {
    public Endpoint {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(port, "port");
    }

    public static Endpoint of(String address, int port) {
        return new Endpoint(IpAddress.parse(address), Port.of(port));
    }

    /** Parses {@code 10.0.0.1:8583} or {@code [2001:db8::1]:8583}, the format {@link #toString()} writes. */
    public static Endpoint parse(String text) {
        String value = text.strip();
        int colon = value.lastIndexOf(':');
        if (colon <= 0) {
            throw new InvalidAddressException("Expected address:port, got '" + value + "'");
        }
        String host = value.substring(0, colon);
        if (host.indexOf(':') >= 0 && !(host.startsWith("[") && host.endsWith("]"))) {
            throw new InvalidAddressException("Write IPv6 endpoints with brackets, e.g. [::1]:8583");
        }
        return new Endpoint(IpAddress.parse(host), Port.of(Integer.parseInt(value.substring(colon + 1))));
    }

    /** {@code 10.0.0.1:8583} or, for IPv6, {@code [2001:db8::1]:8583}. */
    @Override
    public String toString() {
        return address.family() == IpFamily.IPV6 ? "[" + address + "]:" + port : address + ":" + port;
    }
}
