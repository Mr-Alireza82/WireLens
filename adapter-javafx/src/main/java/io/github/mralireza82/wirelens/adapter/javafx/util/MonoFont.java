package io.github.mralireza82.wirelens.adapter.javafx.util;

import javafx.scene.text.Font;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Picks the best installed monospaced font. JavaFX CSS cannot express a font fallback list, so the choice is made
 * once at start-up and shipped to the scene as a tiny generated stylesheet for the {@code .mono} class.
 */
public final class MonoFont {
    private static final List<String> PREFERRED = List.of("JetBrains Mono", "Cascadia Mono", "Cascadia Code",
            "Consolas", "SF Mono", "Menlo", "Fira Code", "Source Code Pro", "DejaVu Sans Mono", "Liberation Mono",
            "Monospaced");

    private MonoFont() {
    }

    public static String family() {
        Set<String> installed = Set.copyOf(Font.getFamilies());
        return PREFERRED.stream().filter(installed::contains).findFirst().orElse("Monospaced");
    }

    /** A {@code data:} stylesheet URL that applies the chosen family to {@code .mono} nodes. */
    public static String stylesheet() {
        String css = ".mono, .mono .text, .mono .content { -fx-font-family: \"" + family() + "\"; }";
        return "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }
}
