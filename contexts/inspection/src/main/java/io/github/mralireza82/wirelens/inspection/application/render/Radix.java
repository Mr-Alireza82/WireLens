package io.github.mralireza82.wirelens.inspection.application.render;

import io.github.mralireza82.baseconversion.Base;

/**
 * The numeric bases bytes can be shown in. Each byte is always written with a fixed number of digits (leading
 * zeros kept), so columns line up and the byte count can be read off the text.
 */
public enum Radix {
    BINARY(Base.BINARY, 8, "BIN"),
    OCTAL(Base.OCTAL, 3, "OCT"),
    DECIMAL(Base.DECIMAL, 3, "DEC"),
    HEXADECIMAL(Base.HEXADECIMAL, 2, "HEX");

    private final Base base;
    private final int digitsPerByte;
    private final String shortName;

    Radix(Base base, int digitsPerByte, String shortName) {
        this.base = base;
        this.digitsPerByte = digitsPerByte;
        this.shortName = shortName;
    }

    /** The matching base of the base-conversion library. */
    Base base() {
        return base;
    }

    public int radix() {
        return base.radix();
    }

    /** 8, 3, 3 or 2. */
    public int digitsPerByte() {
        return digitsPerByte;
    }

    /** {@code BIN}, {@code OCT}, {@code DEC} or {@code HEX}. */
    public String shortName() {
        return shortName;
    }
}
