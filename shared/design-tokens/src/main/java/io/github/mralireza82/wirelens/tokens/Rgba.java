package io.github.mralireza82.wirelens.tokens;

import java.util.Locale;

/**
 * A colour as plain numbers, so every GUI toolkit can turn it into its own type (JavaFX CSS, Compose {@code Color}).
 *
 * @param red   0-255
 * @param green 0-255
 * @param blue  0-255
 * @param alpha 0.0 (transparent) to 1.0 (opaque)
 */
public record Rgba(int red, int green, int blue, double alpha) {
    public Rgba {
        channel("red", red);
        channel("green", green);
        channel("blue", blue);
        if (!(alpha >= 0.0 && alpha <= 1.0)) {
            throw new IllegalArgumentException("alpha must be 0.0-1.0: " + alpha);
        }
    }

    /** An opaque colour from {@code #rrggbb}. */
    public static Rgba hex(String hex) {
        if (!hex.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Expected #rrggbb: " + hex);
        }
        int value = Integer.parseInt(hex.substring(1), 16);
        return new Rgba(value >> 16 & 0xFF, value >> 8 & 0xFF, value & 0xFF, 1.0);
    }

    public static Rgba rgba(int red, int green, int blue, double alpha) {
        return new Rgba(red, green, blue, alpha);
    }

    /** {@code #rrggbb} when opaque, otherwise {@code rgba(r, g, b, a)}: valid in CSS and JavaFX CSS. */
    public String css() {
        if (alpha == 1.0) {
            return String.format(Locale.ROOT, "#%02x%02x%02x", red, green, blue);
        }
        String a = Double.toString(alpha);
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %s)", red, green, blue,
                a.endsWith(".0") ? a.substring(0, a.length() - 2) : a);
    }

    /** {@code 0xAARRGGBB}, the form Android and Compose use. */
    public long argb() {
        return Math.round(alpha * 255) << 24 | (long) red << 16 | (long) green << 8 | blue;
    }

    private static void channel(String name, int value) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + " must be 0-255: " + value);
        }
    }
}
