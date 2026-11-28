package io.github.mralireza82.wirelens.presentation.layout;

/**
 * Window height breakpoints (Material 3), in density-independent units. Height matters for landscape phones and POS
 * terminals: a wide but short screen cannot stack a list above an inspector.
 *
 * <pre>
 *   COMPACT    0 &lt;= h &lt; 480
 *   MEDIUM   480 &lt;= h &lt; 900
 *   EXPANDED 900 &lt;= h
 * </pre>
 */
public enum HeightClass {
    COMPACT(0),
    MEDIUM(480),
    EXPANDED(900);

    private final double minHeight;

    HeightClass(double minHeight) {
        this.minHeight = minHeight;
    }

    /** The smallest height (inclusive) that belongs to this class. */
    public double minHeight() {
        return minHeight;
    }

    public static HeightClass of(double heightDp) {
        Breakpoints.requireSize("height", heightDp);
        HeightClass result = COMPACT;
        for (HeightClass candidate : values()) {
            if (heightDp >= candidate.minHeight) {
                result = candidate;
            }
        }
        return result;
    }
}
