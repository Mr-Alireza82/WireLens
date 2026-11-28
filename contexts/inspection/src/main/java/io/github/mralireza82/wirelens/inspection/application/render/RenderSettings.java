package io.github.mralireza82.wirelens.inspection.application.render;

import java.util.Objects;

/**
 * How one panel presents bytes. Immutable: changing a selector creates a new settings object and the view simply
 * re-renders from the raw bytes; the captured data is never touched.
 *
 * @param encoding     how bytes are decoded into text
 * @param radix        how bytes are written as numbers
 * @param separator    placed between the numbers of two bytes (presentation only); may be empty
 * @param bytesPerLine break the numeric view into lines of this many bytes; 0 lets the view wrap freely
 */
public record RenderSettings(TextEncoding encoding, Radix radix, String separator, int bytesPerLine) {
    public static final int MAX_SEPARATOR_LENGTH = 8;
    public static final RenderSettings DEFAULT = new RenderSettings(TextEncoding.ASCII, Radix.HEXADECIMAL, " ", 16);

    public RenderSettings {
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(radix, "radix");
        Objects.requireNonNull(separator, "separator");
        if (separator.length() > MAX_SEPARATOR_LENGTH) {
            throw new IllegalArgumentException("a separator has at most " + MAX_SEPARATOR_LENGTH + " characters");
        }
        if (separator.indexOf('\n') >= 0 || separator.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("a separator cannot contain line breaks");
        }
        if (bytesPerLine < 0 || bytesPerLine > 1024) {
            throw new IllegalArgumentException("bytes per line must be between 0 and 1024");
        }
    }

    public RenderSettings withEncoding(TextEncoding value) {
        return new RenderSettings(value, radix, separator, bytesPerLine);
    }

    public RenderSettings withRadix(Radix value) {
        return new RenderSettings(encoding, value, separator, bytesPerLine);
    }

    public RenderSettings withSeparator(String value) {
        return new RenderSettings(encoding, radix, value, bytesPerLine);
    }

    public RenderSettings withBytesPerLine(int value) {
        return new RenderSettings(encoding, radix, separator, value);
    }

    /** E.g. {@code ASCII · HEX}. */
    public String summary() {
        return encoding.displayName() + " · " + radix.shortName();
    }
}
