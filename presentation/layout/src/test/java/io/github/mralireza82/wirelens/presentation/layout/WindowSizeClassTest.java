package io.github.mralireza82.wirelens.presentation.layout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WindowSizeClassTest {
    @ParameterizedTest
    @CsvSource({
            "0, COMPACT", "359.9, COMPACT", "599.99, COMPACT",
            "600, MEDIUM", "839, MEDIUM",
            "840, EXPANDED", "1199, EXPANDED",
            "1200, LARGE", "1599, LARGE",
            "1600, EXTRA_LARGE", "3840, EXTRA_LARGE"})
    void widthBreakpoints(double width, WidthClass expected) {
        assertThat(WidthClass.of(width)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"0, COMPACT", "479, COMPACT", "480, MEDIUM", "899, MEDIUM", "900, EXPANDED", "2160, EXPANDED"})
    void heightBreakpoints(double height, HeightClass expected) {
        assertThat(HeightClass.of(height)).isEqualTo(expected);
    }

    @Test
    void posTerminalIsCompactWhateverItsPixelCount() {
        double width = WindowSizeClass.toDp(720, 2.0);
        double height = WindowSizeClass.toDp(1280, 2.0);
        assertThat(WindowSizeClass.of(width, height))
                .isEqualTo(new WindowSizeClass(WidthClass.COMPACT, HeightClass.MEDIUM));
    }

    @Test
    void rejectsImpossibleSizes() {
        assertThatThrownBy(() -> WidthClass.of(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HeightClass.of(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WindowSizeClass.toDp(100, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void splitsKeepProportionsAndMinimums() {
        Split split = new Split(0.62, 140, 120);
        assertThat(split.sizes(1000)).containsExactly(new double[] {620, 380}, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(split.sizes(300)).containsExactly(180, 120);
        assertThat(split.sizes(200)[0] + split.sizes(200)[1]).isEqualTo(200);
        assertThatThrownBy(() -> new Split(1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
