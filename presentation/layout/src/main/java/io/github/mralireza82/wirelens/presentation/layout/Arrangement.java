package io.github.mralireza82.wirelens.presentation.layout;

/** How two regions of a screen share the space. */
public enum Arrangement {
    /** Next to each other, in one row. */
    SIDE_BY_SIDE,
    /** One above the other, in one column. */
    STACKED,
    /** Only one is visible; the user switches between them (tabs on desktop, a tab row or navigation on Android). */
    ONE_AT_A_TIME
}
