package io.github.mralireza82.wirelens.presentation.layout;

import java.util.Objects;

/**
 * The size class of a window: which width and height breakpoint it falls in. A screen's layout is chosen from this,
 * never from the exact pixel size, so every device of a similar size gets the same arrangement; within a class the
 * layout stretches (see {@link Split}).
 *
 * <p>Sizes are density-independent: on Android {@code dp = px / density}; in JavaFX scene coordinates already are
 * logical pixels (the OS scaling is applied by the toolkit), which we treat as dp.
 */
public record WindowSizeClass(WidthClass width, HeightClass height) {
    public WindowSizeClass {
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(height, "height");
    }

    public static WindowSizeClass of(double widthDp, double heightDp) {
        return new WindowSizeClass(WidthClass.of(widthDp), HeightClass.of(heightDp));
    }

    /** Converts physical pixels to dp, e.g. an Android screen of 720 px at density 2.0 is 360 dp. */
    public static double toDp(double pixels, double density) {
        if (!(density > 0) || !Double.isFinite(density)) {
            throw new IllegalArgumentException("density must be positive: " + density);
        }
        return pixels / density;
    }
}
