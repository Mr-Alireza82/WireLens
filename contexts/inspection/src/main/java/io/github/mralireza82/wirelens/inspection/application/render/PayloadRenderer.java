package io.github.mralireza82.wirelens.inspection.application.render;

import io.github.mralireza82.baseconversion.ByteEncoding;
import io.github.mralireza82.wirelens.kernel.bytes.Payload;

/**
 * Derives every human-readable view of a payload from its raw bytes. Stateless and thread-safe.
 *
 * <p>Three representations are kept clearly apart:
 * <ul>
 *   <li><b>Numbers</b>: each byte in the chosen base, with the chosen separator ({@link #numbers}).</li>
 *   <li><b>Text</b>: the bytes decoded with the chosen encoding ({@link #text}).</li>
 *   <li><b>Raw</b>: canonical upper-case hex with no separator, the exact bytes ({@link #rawHex}), and the
 *       classic offset/hex/text dump ({@link #hexDump}).</li>
 * </ul>
 */
public final class PayloadRenderer {
    private static final int DUMP_WIDTH = 16;

    /** All bytes in {@code settings.radix()}, separated by {@code settings.separator()}. */
    public String numbers(Payload payload, RenderSettings settings) {
        return numbers(payload, settings, Integer.MAX_VALUE);
    }

    /** At most {@code maxBytes} leading bytes in the chosen base; the caller shows that the rest was cut. */
    public String numbers(Payload payload, RenderSettings settings, int maxBytes) {
        int count = Math.min(payload.size(), Math.max(0, maxBytes));
        Radix radix = settings.radix();
        String separator = settings.separator();
        int perLine = settings.bytesPerLine();
        StringBuilder text = new StringBuilder(count * (radix.digitsPerByte() + separator.length()));
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                if (perLine > 0 && i % perLine == 0) {
                    text.append('\n');
                } else {
                    text.append(separator);
                }
            }
            text.append(ByteDigits.of(radix, payload.unsignedAt(i)));
        }
        return text.toString();
    }

    /** All bytes decoded as text. */
    public String text(Payload payload, TextEncoding encoding) {
        return text(payload, encoding, Integer.MAX_VALUE);
    }

    /** At most {@code maxBytes} leading bytes decoded as text. */
    public String text(Payload payload, TextEncoding encoding, int maxBytes) {
        byte[] bytes = payload.head(maxBytes);
        return encoding.decode(bytes, 0, bytes.length);
    }

    /** The exact bytes as upper-case hex without separators, e.g. {@code 008E}; the canonical copy format. */
    public String rawHex(Payload payload) {
        return ByteEncoding.toHex(payload.toByteArray());
    }

    /**
     * The classic dump: offset, 16 hex bytes, then the same bytes as characters.
     * <pre>00000000  48 65 6C 6C 6F 0D 0A                              |Hello··|</pre>
     */
    public String hexDump(Payload payload, TextEncoding encoding) {
        int size = payload.size();
        StringBuilder dump = new StringBuilder((size / DUMP_WIDTH + 1) * 78);
        for (int lineStart = 0; lineStart < size; lineStart += DUMP_WIDTH) {
            String offset = Integer.toHexString(lineStart).toUpperCase();
            dump.append("0".repeat(Math.max(0, 8 - offset.length()))).append(offset).append("  ");
            for (int i = 0; i < DUMP_WIDTH; i++) {
                int index = lineStart + i;
                if (index < size) {
                    dump.append(ByteDigits.of(Radix.HEXADECIMAL, payload.unsignedAt(index))).append(' ');
                } else {
                    dump.append("   ");
                }
                if (i == 7) {
                    dump.append(' ');
                }
            }
            dump.append(" |");
            for (int i = lineStart; i < Math.min(lineStart + DUMP_WIDTH, size); i++) {
                dump.append(encoding.charForByte(payload.unsignedAt(i)));
            }
            dump.append("|\n");
        }
        return dump.toString();
    }
}
