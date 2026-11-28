package io.github.mralireza82.wirelens.presentation.layout;

/**
 * Window width breakpoints, in density-independent units (dp on Android, logical pixels in JavaFX). These are the
 * Material 3 window size classes, so Android and desktop agree on where a layout changes.
 *
 * <pre>
 *   COMPACT       0 &lt;= w &lt;  600   phones, most POS terminals (portrait)
 *   MEDIUM      600 &lt;= w &lt;  840   tablets (portrait), large POS screens, small windows
 *   EXPANDED    840 &lt;= w &lt; 1200   tablets (landscape), laptop windows
 *   LARGE      1200 &lt;= w &lt; 1600   desktop
 *   EXTRA_LARGE 1600 &lt;= w          wide desktop monitors
 * </pre>
 */
public enum WidthClass {
    COMPACT(0),
    MEDIUM(600),
    EXPANDED(840),
    LARGE(1200),
    EXTRA_LARGE(1600);

    private final double minWidth;

    WidthClass(double minWidth) {
        this.minWidth = minWidth;
    }

    /** The smallest width (inclusive) that belongs to this class. */
    public double minWidth() {
        return minWidth;
    }

    public static WidthClass of(double widthDp) {
        Breakpoints.requireSize("width", widthDp);
        WidthClass result = COMPACT;
        for (WidthClass candidate : values()) {
            if (widthDp >= candidate.minWidth) {
                result = candidate;
            }
        }
        return result;
    }

    public boolean isAtLeast(WidthClass other) {
        return compareTo(other) >= 0;
    }
}
