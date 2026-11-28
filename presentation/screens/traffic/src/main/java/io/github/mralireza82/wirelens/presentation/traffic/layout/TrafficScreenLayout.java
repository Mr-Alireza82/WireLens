package io.github.mralireza82.wirelens.presentation.traffic.layout;

import io.github.mralireza82.wirelens.presentation.layout.Arrangement;
import io.github.mralireza82.wirelens.presentation.layout.HeightClass;
import io.github.mralireza82.wirelens.presentation.layout.Split;
import io.github.mralireza82.wirelens.presentation.layout.WidthClass;
import io.github.mralireza82.wirelens.presentation.layout.WindowSizeClass;

import java.util.Objects;

/**
 * How the traffic screen looks for one window size class. The screen has a header (title, status, statistics) and
 * two panels (source = client side, destination side); each panel has a traffic list and an inspector.
 *
 * <p>This is the single source of the screen's layout rules: every GUI adapter draws exactly this, which is what
 * makes the app look the same on every platform at the same size.
 *
 * @param panels           how the source and destination panels share the window
 * @param panelSplit       their proportions when both are visible
 * @param listAndInspector how each panel's list and inspector share the panel
 * @param listSplit        their proportions (the list is the first region)
 * @param header           how much of the header is shown
 */
public record TrafficScreenLayout(Arrangement panels, Split panelSplit, Arrangement listAndInspector,
                                  Split listSplit, HeaderDensity header) {
    /** Below this a side-by-side panel is too narrow for its toolbars. */
    public static final double PANEL_MIN_DP = 360;
    static final Split PANELS = Split.even(PANEL_MIN_DP);
    static final Split LIST_OVER_INSPECTOR = new Split(0.62, 140, 120);
    static final Split LIST_BESIDE_INSPECTOR = new Split(0.55, 260, 220);

    public TrafficScreenLayout {
        Objects.requireNonNull(panels, "panels");
        Objects.requireNonNull(panelSplit, "panelSplit");
        Objects.requireNonNull(listAndInspector, "listAndInspector");
        Objects.requireNonNull(listSplit, "listSplit");
        Objects.requireNonNull(header, "header");
        if (listAndInspector == Arrangement.ONE_AT_A_TIME) {
            throw new IllegalArgumentException("the inspector is always visible next to or below its list");
        }
    }

    /** How much of the header to show. */
    public enum HeaderDensity {
        /** Title row with the route, plus the statistics tiles and throughput chart. */
        FULL,
        /** Title row only (no route text, no statistics): for narrow or short windows. */
        CONDENSED
    }

    /**
     * The rules. Width decides how the two panels share the window; height decides whether a panel can stack its
     * list above its inspector or must put them side by side.
     *
     * <pre>
     *   width \ height   COMPACT (&lt;480)                MEDIUM / EXPANDED
     *   COMPACT          one panel at a time, stacked    one panel at a time, stacked
     *   MEDIUM           one at a time, side by side     panels stacked
     *   EXPANDED+        panels side by side, side by    panels side by side, stacked
     *                    side inside each panel
     * </pre>
     *
     * The header is condensed below EXPANDED width or at COMPACT height.
     */
    public static TrafficScreenLayout forSize(WindowSizeClass size) {
        Objects.requireNonNull(size, "size");
        boolean shortWindow = size.height() == HeightClass.COMPACT;
        WidthClass width = size.width();

        Arrangement panels;
        if (width.isAtLeast(WidthClass.EXPANDED)) {
            panels = Arrangement.SIDE_BY_SIDE;
        } else if (width == WidthClass.MEDIUM && !shortWindow) {
            panels = Arrangement.STACKED;
        } else {
            panels = Arrangement.ONE_AT_A_TIME;
        }

        boolean inspectorBeside = shortWindow && width.isAtLeast(WidthClass.MEDIUM);
        Arrangement listAndInspector = inspectorBeside ? Arrangement.SIDE_BY_SIDE : Arrangement.STACKED;
        Split listSplit = inspectorBeside ? LIST_BESIDE_INSPECTOR : LIST_OVER_INSPECTOR;

        HeaderDensity header = width.isAtLeast(WidthClass.EXPANDED) && !shortWindow
                ? HeaderDensity.FULL : HeaderDensity.CONDENSED;
        return new TrafficScreenLayout(panels, PANELS, listAndInspector, listSplit, header);
    }
}
