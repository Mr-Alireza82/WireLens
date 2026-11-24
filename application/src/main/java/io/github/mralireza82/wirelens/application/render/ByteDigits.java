package io.github.mralireza82.wirelens.application.render;

import io.github.mralireza82.baseconversion.BaseConversion;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.Map;

/**
 * Lookup tables with the digits of every byte value (0..255) in every {@link Radix} (Flyweight).
 *
 * <p>The tables are built once from the base-conversion library ({@link BaseConversion#toDigits}) and padded to
 * a fixed width. Rendering a payload is then a table lookup per byte, which keeps re-rendering thousands of rows
 * on a base change instant.
 */
final class ByteDigits {
    private static final Map<Radix, char[][]> TABLES = new EnumMap<>(Radix.class);

    static {
        for (Radix radix : Radix.values()) {
            char[][] table = new char[256][];
            for (int value = 0; value < 256; value++) {
                String digits = BaseConversion.toDigits(BigInteger.valueOf(value), radix.base());
                table[value] = ("0".repeat(radix.digitsPerByte() - digits.length()) + digits).toCharArray();
            }
            TABLES.put(radix, table);
        }
    }

    private ByteDigits() {
    }

    /** The fixed-width digits of {@code unsignedByte}; the returned array must not be modified. */
    static char[] of(Radix radix, int unsignedByte) {
        return TABLES.get(radix)[unsignedByte];
    }
}
