package io.github.mralireza82.wirelens.adapter.gui.javafx.util;

import io.github.mralireza82.wirelens.tokens.ColorScheme;
import io.github.mralireza82.wirelens.tokens.DesignTokens;
import io.github.mralireza82.wirelens.tokens.Palette;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TokenStylesheetTest {
    @Test
    void mapsEveryPaletteColourInBothThemes() {
        for (ColorScheme scheme : ColorScheme.values()) {
            Map<String, String> variables = TokenStylesheet.variables(scheme);
            assertThat(variables).hasSize(Palette.class.getRecordComponents().length);
            assertThat(variables).containsEntry("-color-fg-muted", DesignTokens.palette(scheme).fgMuted().css())
                    .containsEntry("-wl-source", "#2f81f7");
        }
        assertThat(TokenStylesheet.css(ColorScheme.DARK)).contains("-color-bg-default: #0d1117;");
        assertThat(TokenStylesheet.css(ColorScheme.LIGHT)).contains("-color-bg-default: #ffffff;");
    }

    @Test
    void wirelensCssOnlyUsesColoursThatComeFromTokensOrTheTheme() throws IOException {
        String css;
        try (InputStream in = TokenStylesheetTest.class.getResourceAsStream(
                "/io/github/mralireza82/wirelens/adapter/gui/javafx/wirelens.css")) {
            assertThat(in).isNotNull();
            css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // No hard-coded hex colours: they belong in shared/design-tokens.
        assertThat(css).doesNotContainPattern("#[0-9a-fA-F]{3,8}\\b");
        Matcher own = Pattern.compile("-wl-[a-z-]+").matcher(css);
        while (own.find()) {
            assertThat(TokenStylesheet.variables(ColorScheme.DARK)).containsKey(own.group());
        }
    }
}
