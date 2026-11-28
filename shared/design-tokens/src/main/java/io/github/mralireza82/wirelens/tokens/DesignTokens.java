package io.github.mralireza82.wirelens.tokens;

import static io.github.mralireza82.wirelens.tokens.Rgba.hex;
import static io.github.mralireza82.wirelens.tokens.Rgba.rgba;

/**
 * The single source of WireLens' look for every GUI adapter: colours per theme, type scale, spacing and corner
 * radii. Sizes are in density-independent units (dp on Android, logical pixels in JavaFX), never physical pixels.
 */
public final class DesignTokens {
    private static final Rgba SOURCE = hex("#2f81f7");
    private static final Rgba DESTINATION = hex("#a371f7");

    public static final Palette DARK = new Palette(
            hex("#0d1117"), hex("#161b22"), hex("#0d1117"),
            hex("#c9d1d9"), hex("#8b949e"), hex("#6e7681"), hex("#ffffff"),
            hex("#30363d"), hex("#21262d"),
            hex("#58a6ff"), hex("#1f6feb"), rgba(56, 139, 253, 0.15),
            hex("#3fb950"), hex("#238636"), rgba(46, 160, 67, 0.15), rgba(46, 160, 67, 0.4),
            hex("#d29922"), hex("#9e6a03"), rgba(187, 128, 9, 0.15),
            hex("#f85149"), hex("#da3633"), rgba(248, 81, 73, 0.15),
            rgba(110, 118, 129, 0.1),
            SOURCE, DESTINATION);

    public static final Palette LIGHT = new Palette(
            hex("#ffffff"), hex("#f6f8fa"), hex("#ffffff"),
            hex("#24292f"), hex("#57606a"), hex("#6e7781"), hex("#ffffff"),
            hex("#d0d7de"), hex("#d0d7de"),
            hex("#0969da"), hex("#0969da"), hex("#ddf4ff"),
            hex("#1a7f37"), hex("#2da44e"), hex("#dafbe1"), rgba(74, 194, 107, 0.4),
            hex("#9a6700"), hex("#bf8700"), hex("#fff8c5"),
            hex("#cf222e"), hex("#cf222e"), hex("#ffebe9"),
            rgba(234, 238, 242, 0.5),
            SOURCE, DESTINATION);

    private DesignTokens() {
    }

    public static Palette palette(ColorScheme scheme) {
        return switch (scheme) {
            case DARK -> DARK;
            case LIGHT -> LIGHT;
        };
    }

    /** Spacing steps; layouts use these instead of arbitrary numbers. */
    public enum Spacing {
        XS(4), S(8), M(12), L(16), XL(24);

        private final int units;

        Spacing(int units) {
            this.units = units;
        }

        public int units() {
            return units;
        }
    }

    /** Corner radii. */
    public enum Radius {
        S(4), M(6), L(8), PILL(999);

        private final int units;

        Radius(int units) {
            this.units = units;
        }

        public int units() {
            return units;
        }
    }

    /** Text sizes relative to the platform's body size (1.0), so the user's font scaling keeps working. */
    public enum TypeScale {
        CAPTION(0.82), SMALL(0.9), BODY(1.0), TITLE(1.15), HEADLINE(1.4);

        private final double scale;

        TypeScale(double scale) {
            this.scale = scale;
        }

        public double scale() {
            return scale;
        }
    }
}
