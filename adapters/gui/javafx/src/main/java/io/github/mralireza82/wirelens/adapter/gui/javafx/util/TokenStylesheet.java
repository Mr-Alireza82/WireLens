package io.github.mralireza82.wirelens.adapter.gui.javafx.util;

import io.github.mralireza82.wirelens.tokens.ColorScheme;
import io.github.mralireza82.wirelens.tokens.DesignTokens;
import io.github.mralireza82.wirelens.tokens.Palette;
import io.github.mralireza82.wirelens.tokens.Rgba;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Turns the shared design tokens into JavaFX CSS: every palette colour becomes a looked-up colour on {@code .root}.
 * The AtlantaFX theme and {@code wirelens.css} only refer to these names, so the colours have one source for every
 * GUI adapter. Every component of {@link Palette} is mapped automatically, so a new token cannot be forgotten here.
 *
 * <p>Names: {@code fgMuted} becomes {@code -color-fg-muted} (the AtlantaFX variable it overrides); WireLens' own
 * {@code source} and {@code destination} become {@code -wl-source} and {@code -wl-destination}.
 */
public final class TokenStylesheet {
    private static final Set<String> OWN = Set.of("source", "destination");

    private TokenStylesheet() {
    }

    /** CSS variable name to value, in palette order. */
    public static Map<String, String> variables(ColorScheme scheme) {
        Palette palette = DesignTokens.palette(scheme);
        Map<String, String> variables = new LinkedHashMap<>();
        for (RecordComponent component : Palette.class.getRecordComponents()) {
            String name = component.getName();
            String css = (OWN.contains(name) ? "-wl-" : "-color-") + kebab(name);
            variables.put(css, colour(palette, component).css());
        }
        return variables;
    }

    public static String css(ColorScheme scheme) {
        StringBuilder css = new StringBuilder(".root {\n");
        variables(scheme).forEach((name, value) -> css.append("    ").append(name).append(": ").append(value)
                .append(";\n"));
        return css.append("}\n").toString();
    }

    /** A {@code data:} stylesheet URL for {@link javafx.scene.Scene#getStylesheets()}. */
    public static String url(ColorScheme scheme) {
        return "data:text/css;base64," + Base64.getEncoder().encodeToString(css(scheme).getBytes(StandardCharsets.UTF_8));
    }

    private static Rgba colour(Palette palette, RecordComponent component) {
        try {
            return (Rgba) component.getAccessor().invoke(palette);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Cannot read token " + component.getName(), e);
        }
    }

    private static String kebab(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(java.util.Locale.ROOT);
    }
}
