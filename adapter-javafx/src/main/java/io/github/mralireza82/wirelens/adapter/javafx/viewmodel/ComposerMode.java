package io.github.mralireza82.wirelens.adapter.javafx.viewmodel;

/** How the send box reads what the user types. */
public enum ComposerMode {
    /** Text in the panel's encoding, with escapes such as {@code \r\n} and {@code \x02}. */
    TEXT,
    /** Byte values in the panel's base, e.g. {@code 02 00 8E 03}. */
    NUMBERS
}
