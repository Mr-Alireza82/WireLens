package io.github.mralireza82.wirelens.tokens;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DesignTokensTest {
    @Test
    void bothThemesShareTheRelayColours() {
        assertThat(DesignTokens.palette(ColorScheme.DARK).source()).isEqualTo(DesignTokens.LIGHT.source());
        assertThat(DesignTokens.palette(ColorScheme.DARK).destination()).isEqualTo(DesignTokens.LIGHT.destination());
        assertThat(DesignTokens.DARK.bgDefault()).isNotEqualTo(DesignTokens.LIGHT.bgDefault());
    }

    @Test
    void writesCssAndArgb() {
        assertThat(Rgba.hex("#0D1117").css()).isEqualTo("#0d1117");
        assertThat(Rgba.rgba(56, 139, 253, 0.15).css()).isEqualTo("rgba(56, 139, 253, 0.15)");
        assertThat(Rgba.hex("#2f81f7").argb()).isEqualTo(0xFF2F81F7L);
        assertThat(Rgba.rgba(0, 0, 0, 0.0).argb()).isZero();
    }

    @Test
    void rejectsInvalidColours() {
        assertThatThrownBy(() -> Rgba.hex("2f81f7")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Rgba.rgba(256, 0, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Rgba.rgba(0, 0, 0, 1.5)).isInstanceOf(IllegalArgumentException.class);
    }
}
