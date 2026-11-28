package io.github.mralireza82.wirelens.tokens;

import java.util.Objects;

/**
 * Semantic colours, named by purpose (background, muted text, danger, ...) and never by hue, so both themes and every
 * toolkit use the same names. A GUI adapter must map every component; each adapter has a test that checks it.
 *
 * <p>Values follow GitHub's Primer palette, which the JavaFX look (AtlantaFX Primer) is based on. {@code source} and
 * {@code destination} are WireLens' own colours for the client side and the destination side of the relay.
 */
public record Palette(
        Rgba bgDefault, Rgba bgSubtle, Rgba bgOverlay,
        Rgba fgDefault, Rgba fgMuted, Rgba fgSubtle, Rgba fgEmphasis,
        Rgba borderDefault, Rgba borderMuted,
        Rgba accentFg, Rgba accentEmphasis, Rgba accentSubtle,
        Rgba successFg, Rgba successEmphasis, Rgba successSubtle, Rgba successMuted,
        Rgba warningFg, Rgba warningEmphasis, Rgba warningSubtle,
        Rgba dangerFg, Rgba dangerEmphasis, Rgba dangerSubtle,
        Rgba neutralSubtle,
        Rgba source, Rgba destination) {

    public Palette {
        for (Rgba colour : new Rgba[] {bgDefault, bgSubtle, bgOverlay, fgDefault, fgMuted, fgSubtle, fgEmphasis,
                borderDefault, borderMuted, accentFg, accentEmphasis, accentSubtle, successFg, successEmphasis,
                successSubtle, successMuted, warningFg, warningEmphasis, warningSubtle, dangerFg, dangerEmphasis,
                dangerSubtle, neutralSubtle, source, destination}) {
            Objects.requireNonNull(colour, "every palette colour is required");
        }
    }
}
