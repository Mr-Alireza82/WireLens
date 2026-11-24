package io.github.mralireza82.wirelens.domain.net;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An IPv4 or IPv6 address literal (value object).
 *
 * <p>Parsing is strict and never touches DNS: host names, IPv4 octets with leading zeros (ambiguous: octal or
 * decimal?), IPv6 zone IDs and anything else that is not a plain literal are rejected with a message that can be
 * shown to the user. {@link #toString()} returns the canonical text: dotted decimal for IPv4 and the RFC 5952
 * form for IPv6 (lower case, shortest, longest zero run compressed).
 */
public final class IpAddress {
    private static final int IPV6_GROUPS = 8;

    private final byte[] octets;
    private final IpFamily family;
    private final String canonical;

    private IpAddress(byte[] octets) {
        this.octets = octets;
        this.family = octets.length == 4 ? IpFamily.IPV4 : IpFamily.IPV6;
        this.canonical = family == IpFamily.IPV4 ? formatIpv4(octets, 0) : formatIpv6(octets);
    }

    /** Parses an address literal. IPv6 may be wrapped in brackets, e.g. {@code [::1]}. */
    public static IpAddress parse(String text) {
        Objects.requireNonNull(text, "text");
        String value = text.strip();
        if (value.isEmpty()) {
            throw new InvalidAddressException("Enter an IP address");
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.indexOf(':') >= 0) {
            return new IpAddress(parseIpv6(value));
        }
        if (value.indexOf('.') >= 0 && looksNumeric(value)) {
            return new IpAddress(parseIpv4(value));
        }
        throw new InvalidAddressException("'" + text.strip() + "' is not an IPv4 or IPv6 address");
    }

    /** Like {@link #parse(String)} but returns empty instead of throwing. */
    public static Optional<IpAddress> tryParse(String text) {
        try {
            return Optional.of(parse(text));
        } catch (InvalidAddressException e) {
            return Optional.empty();
        }
    }

    /** Returns an error message for invalid text, or empty when the text is a valid address. */
    public static Optional<String> validate(String text) {
        try {
            parse(text);
            return Optional.empty();
        } catch (InvalidAddressException e) {
            return Optional.of(e.getMessage());
        }
    }

    /** Wraps raw network-order bytes: 4 for IPv4, 16 for IPv6. */
    public static IpAddress ofOctets(byte[] octets) {
        Objects.requireNonNull(octets, "octets");
        if (octets.length != 4 && octets.length != 16) {
            throw new InvalidAddressException("an IP address has 4 or 16 bytes, not " + octets.length);
        }
        return new IpAddress(octets.clone());
    }

    /** {@code 0.0.0.0}: listen on every IPv4 interface. */
    public static IpAddress anyIpv4() {
        return new IpAddress(new byte[4]);
    }

    /** {@code 127.0.0.1}. */
    public static IpAddress loopbackIpv4() {
        return new IpAddress(new byte[] {127, 0, 0, 1});
    }

    public IpFamily family() {
        return family;
    }

    /** A copy of the address bytes in network order. */
    public byte[] octets() {
        return octets.clone();
    }

    /** True for {@code 0.0.0.0} and {@code ::}, the "all interfaces" wildcard. */
    public boolean isAnyLocal() {
        for (byte b : octets) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    /** True for {@code 127.0.0.0/8} and {@code ::1}. */
    public boolean isLoopback() {
        if (family == IpFamily.IPV4) {
            return octets[0] == 127;
        }
        for (int i = 0; i < 15; i++) {
            if (octets[i] != 0) {
                return false;
            }
        }
        return octets[15] == 1;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof IpAddress address && Arrays.equals(octets, address.octets);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(octets);
    }

    /** The canonical text form, e.g. {@code 10.0.0.1} or {@code 2001:db8::1}. */
    @Override
    public String toString() {
        return canonical;
    }

    // ---------------------------------------------------------------- IPv4

    private static boolean looksNumeric(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '.' && (c < '0' || c > '9')) {
                return false;
            }
        }
        return true;
    }

    private static byte[] parseIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            throw new InvalidAddressException("An IPv4 address has 4 numbers separated by dots, e.g. 192.168.1.10");
        }
        byte[] result = new byte[4];
        for (int i = 0; i < 4; i++) {
            result[i] = (byte) parseIpv4Octet(parts[i], i + 1);
        }
        return result;
    }

    private static int parseIpv4Octet(String part, int position) {
        if (part.isEmpty()) {
            throw new InvalidAddressException("Number " + position + " of the IPv4 address is missing");
        }
        if (part.length() > 3) {
            throw new InvalidAddressException("'" + part + "' is too long; each IPv4 number is 0 to 255");
        }
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            if (c < '0' || c > '9') {
                throw new InvalidAddressException("'" + part + "' is not a decimal number");
            }
        }
        if (part.length() > 1 && part.charAt(0) == '0') {
            throw new InvalidAddressException("'" + part + "' has a leading zero, which is ambiguous; write "
                    + Integer.parseInt(part));
        }
        int value = Integer.parseInt(part);
        if (value > 255) {
            throw new InvalidAddressException(value + " is out of range; each IPv4 number is 0 to 255");
        }
        return value;
    }

    // ---------------------------------------------------------------- IPv6

    private static byte[] parseIpv6(String value) {
        if (value.indexOf('%') >= 0) {
            throw new InvalidAddressException("IPv6 zone IDs (the part after %) are not supported");
        }
        int compression = value.indexOf("::");
        if (compression >= 0 && value.indexOf("::", compression + 1) >= 0) {
            throw new InvalidAddressException("'::' may appear only once in an IPv6 address");
        }
        List<Integer> head;
        List<Integer> tail;
        if (compression < 0) {
            head = parseIpv6Groups(value, true);
            tail = List.of();
            if (head.size() != IPV6_GROUPS) {
                throw new InvalidAddressException("An IPv6 address has 8 groups (or uses '::'), found " + head.size());
            }
        } else {
            String before = value.substring(0, compression);
            String after = value.substring(compression + 2);
            head = before.isEmpty() ? List.of() : parseIpv6Groups(before, false);
            tail = after.isEmpty() ? List.of() : parseIpv6Groups(after, true);
            if (head.size() + tail.size() > IPV6_GROUPS - 1) {
                throw new InvalidAddressException("Too many groups for an IPv6 address that uses '::'");
            }
        }
        byte[] result = new byte[16];
        int index = 0;
        for (int group : head) {
            result[index++] = (byte) (group >> 8);
            result[index++] = (byte) group;
        }
        index = 16 - tail.size() * 2;
        for (int group : tail) {
            result[index++] = (byte) (group >> 8);
            result[index++] = (byte) group;
        }
        return result;
    }

    /** Parses colon-separated groups; an embedded IPv4 address is allowed only as the very last part. */
    private static List<Integer> parseIpv6Groups(String text, boolean mayEndWithIpv4) {
        String[] parts = text.split(":", -1);
        List<Integer> groups = new ArrayList<>(IPV6_GROUPS);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            boolean last = i == parts.length - 1;
            if (part.isEmpty()) {
                throw new InvalidAddressException("An IPv6 address has an empty group; use '::' once to skip zeros");
            }
            if (part.indexOf('.') >= 0) {
                if (!last || !mayEndWithIpv4) {
                    throw new InvalidAddressException("An embedded IPv4 address must be the last part of an IPv6 address");
                }
                byte[] ipv4 = parseIpv4(part);
                groups.add((ipv4[0] & 0xFF) << 8 | ipv4[1] & 0xFF);
                groups.add((ipv4[2] & 0xFF) << 8 | ipv4[3] & 0xFF);
                continue;
            }
            if (part.length() > 4) {
                throw new InvalidAddressException("IPv6 group '" + part + "' has more than 4 hex digits");
            }
            int group = 0;
            for (int c = 0; c < part.length(); c++) {
                int digit = Character.digit(part.charAt(c), 16);
                if (digit < 0) {
                    throw new InvalidAddressException("'" + part.charAt(c) + "' is not a hexadecimal digit");
                }
                group = group << 4 | digit;
            }
            groups.add(group);
        }
        return groups;
    }

    // ---------------------------------------------------------------- formatting

    private static String formatIpv4(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) + "." + (bytes[offset + 1] & 0xFF) + "."
                + (bytes[offset + 2] & 0xFF) + "." + (bytes[offset + 3] & 0xFF);
    }

    /** RFC 5952 text, with the IPv4-mapped form {@code ::ffff:a.b.c.d} written in mixed notation. */
    private static String formatIpv6(byte[] bytes) {
        int[] groups = new int[IPV6_GROUPS];
        for (int i = 0; i < IPV6_GROUPS; i++) {
            groups[i] = (bytes[2 * i] & 0xFF) << 8 | bytes[2 * i + 1] & 0xFF;
        }
        boolean ipv4Mapped = groups[0] == 0 && groups[1] == 0 && groups[2] == 0 && groups[3] == 0
                && groups[4] == 0 && groups[5] == 0xFFFF;
        if (ipv4Mapped) {
            return "::ffff:" + formatIpv4(bytes, 12);
        }
        int bestStart = -1;
        int bestLength = 0;
        for (int i = 0; i < IPV6_GROUPS; ) {
            if (groups[i] != 0) {
                i++;
                continue;
            }
            int start = i;
            while (i < IPV6_GROUPS && groups[i] == 0) {
                i++;
            }
            if (i - start > bestLength) {
                bestStart = start;
                bestLength = i - start;
            }
        }
        if (bestLength < 2) {
            bestStart = -1;
        }
        StringBuilder text = new StringBuilder(39);
        for (int i = 0; i < IPV6_GROUPS; i++) {
            if (i == bestStart) {
                text.append("::");
                i += bestLength - 1;
                continue;
            }
            if (!text.isEmpty() && text.charAt(text.length() - 1) != ':') {
                text.append(':');
            }
            text.append(Integer.toHexString(groups[i]));
        }
        return text.toString();
    }
}
