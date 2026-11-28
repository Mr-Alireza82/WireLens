package io.github.mralireza82.wirelens.presentation.traffic.layout;

import io.github.mralireza82.wirelens.presentation.layout.Arrangement;
import io.github.mralireza82.wirelens.presentation.layout.HeightClass;
import io.github.mralireza82.wirelens.presentation.layout.WidthClass;
import io.github.mralireza82.wirelens.presentation.layout.WindowSizeClass;
import io.github.mralireza82.wirelens.presentation.traffic.layout.TrafficScreenLayout.HeaderDensity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficScreenLayoutTest {
    private static TrafficScreenLayout at(double width, double height) {
        return TrafficScreenLayout.forSize(WindowSizeClass.of(width, height));
    }

    @Test
    void desktopShowsBothPanelsSideBySide() {
        TrafficScreenLayout layout = at(1440, 900);
        assertThat(layout.panels()).isEqualTo(Arrangement.SIDE_BY_SIDE);
        assertThat(layout.listAndInspector()).isEqualTo(Arrangement.STACKED);
        assertThat(layout.header()).isEqualTo(HeaderDensity.FULL);
        assertThat(layout.panelSplit().firstFraction()).isEqualTo(0.5);
    }

    @Test
    void mediumWidthStacksThePanels() {
        TrafficScreenLayout layout = at(700, 1000);
        assertThat(layout.panels()).isEqualTo(Arrangement.STACKED);
        assertThat(layout.header()).isEqualTo(HeaderDensity.CONDENSED);
    }

    @Test
    void phonesAndPosTerminalsShowOnePanelAtATime() {
        TrafficScreenLayout portraitPos = at(360, 640);
        assertThat(portraitPos.panels()).isEqualTo(Arrangement.ONE_AT_A_TIME);
        assertThat(portraitPos.listAndInspector()).isEqualTo(Arrangement.STACKED);
        assertThat(portraitPos.header()).isEqualTo(HeaderDensity.CONDENSED);
    }

    @Test
    void shortWindowsPutTheInspectorBesideTheList() {
        TrafficScreenLayout landscapePhone = at(780, 360);
        assertThat(landscapePhone.panels()).isEqualTo(Arrangement.ONE_AT_A_TIME);
        assertThat(landscapePhone.listAndInspector()).isEqualTo(Arrangement.SIDE_BY_SIDE);

        TrafficScreenLayout shortDesktop = at(1300, 420);
        assertThat(shortDesktop.panels()).isEqualTo(Arrangement.SIDE_BY_SIDE);
        assertThat(shortDesktop.listAndInspector()).isEqualTo(Arrangement.SIDE_BY_SIDE);
        assertThat(shortDesktop.header()).isEqualTo(HeaderDensity.CONDENSED);
    }

    @Test
    void everySizeClassHasAValidLayoutThatFitsItsSmallestWindow() {
        for (WidthClass width : WidthClass.values()) {
            for (HeightClass height : HeightClass.values()) {
                TrafficScreenLayout layout = TrafficScreenLayout.forSize(new WindowSizeClass(width, height));
                assertThat(layout.listAndInspector()).isNotEqualTo(Arrangement.ONE_AT_A_TIME);
                if (layout.panels() == Arrangement.SIDE_BY_SIDE) {
                    assertThat(width.minWidth()).as("two panels must fit at %s", width)
                            .isGreaterThanOrEqualTo(2 * TrafficScreenLayout.PANEL_MIN_DP);
                }
            }
        }
    }
}
