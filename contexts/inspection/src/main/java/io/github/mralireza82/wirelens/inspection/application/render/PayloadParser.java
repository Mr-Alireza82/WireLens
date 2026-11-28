package io.github.mralireza82.wirelens.inspection.application.render;

import io.github.mralireza82.baseconversion.BaseConversion;
import io.github.mralireza82.baseconversion.ByteEncoding;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Objects;

/**
 * Turns what a user types in the send box into bytes: either numbers in a base, or text with escapes.
 *
 * <p>Numbers may be separated by spaces, commas, colons, semicolons, dashes or underscores, and may carry a
 * {@code 0x}/{@code 0o}/{@code 0b} prefix. In hexadecimal and binary an unseparated run is split into bytes
 * ({@code 008E} is two bytes), exactly as the base-conversion library's {@link ByteEncoding} reads data.
 */
public final class PayloadParser {
    private static final BigInteger BYTE_MAX = BigInteger.valueOf(255);

    /** Parses numbers written in {@code radix}. */
    public byte[] parseNumbers(String input, Radix radix) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(radix, "radix");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String[] tokens = input.strip().split("[\\s,;:_\\-]+");
        int position = 0;
        for (String raw : tokens) {
            if (raw.isEmpty()) {
                continue;
            }
            position++;
            String token = withoutPrefix(raw, radix);
            if (token.isEmpty()) {
                throw new PayloadSyntaxException("Value " + position + " ('" + raw + "') has a prefix but no digits");
            }
            try {
                if (radix == Radix.HEXADECIMAL && token.length() > 2) {
                    out.writeBytes(ByteEncoding.fromHex(token));
                } else if (radix == Radix.BINARY && token.length() > 8) {
                    out.writeBytes(ByteEncoding.fromBits(token));
                } else {
                    BigInteger value = BaseConversion.toNumber(token, radix.base());
                    if (value.compareTo(BYTE_MAX) > 0) {
                        throw new PayloadSyntaxException("Value " + position + " ('" + raw
                                + "') is larger than one byte (255)");
                    }
                    out.write(value.intValue());
                }
            } catch (PayloadSyntaxException e) {
                throw e;
            } catch (IllegalArgumentException e) {
                throw new PayloadSyntaxException("Value " + position + " ('" + raw + "'): " + e.getMessage());
            }
        }
        if (out.size() == 0) {
            throw new PayloadSyntaxException("Nothing to send");
        }
        return out.toByteArray();
    }

    /**
     * Encodes text, understanding the escapes {@code \r \n \t \0 \\} and {@code \xHH} (one raw byte, e.g.
     * {@code \x02} for STX). Raw bytes are inserted as-is, not encoded.
     */
    public byte[] parseText(String input, TextEncoding encoding) {
        Objects.requireNonNull(input, "input");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StringBuilder pending = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c != '\\') {
                pending.append(c);
                continue;
            }
            if (i + 1 >= input.length()) {
                throw new PayloadSyntaxException("A backslash at the end needs a character after it (use \\\\)");
            }
            char next = input.charAt(++i);
            switch (next) {
                case 'r' -> pending.append('\r');
                case 'n' -> pending.append('\n');
                case 't' -> pending.append('\t');
                case '0' -> pending.append('\0');
                case '\\' -> pending.append('\\');
                case 'x', 'X' -> {
                    if (i + 2 >= input.length()) {
                        throw new PayloadSyntaxException("\\x needs two hex digits, e.g. \\x02");
                    }
                    String hex = input.substring(i + 1, i + 3);
                    flush(pending, encoding, out);
                    try {
                        out.writeBytes(ByteEncoding.fromHex(hex));
                    } catch (IllegalArgumentException e) {
                        throw new PayloadSyntaxException("\\x" + hex + " is not a hex byte");
                    }
                    i += 2;
                }
                default -> throw new PayloadSyntaxException("Unknown escape \\" + next
                        + " (use \\r \\n \\t \\0 \\\\ or \\xHH)");
            }
        }
        flush(pending, encoding, out);
        if (out.size() == 0) {
            throw new PayloadSyntaxException("Nothing to send");
        }
        return out.toByteArray();
    }

    private static void flush(StringBuilder pending, TextEncoding encoding, ByteArrayOutputStream out) {
        if (!pending.isEmpty()) {
            out.writeBytes(encoding.encode(pending.toString()));
            pending.setLength(0);
        }
    }

    private static String withoutPrefix(String token, Radix radix) {
        String lower = token.toLowerCase(Locale.ROOT);
        String prefix = switch (radix) {
            case HEXADECIMAL -> "0x";
            case BINARY -> "0b";
            case OCTAL -> "0o";
            case DECIMAL -> "";
        };
        return !prefix.isEmpty() && lower.startsWith(prefix) ? token.substring(2) : token;
    }
}
