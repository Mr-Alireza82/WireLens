package io.github.mralireza82.wirelens.presentation.layout;

/**
 * How two regions divide the available space: a fraction, not a size, so the layout resizes smoothly within a size
 * class. Minimums (in dp) keep either region usable; the GUI adapter enforces them.
 *
 * @param firstFraction share of the space for the first region, between 0 and 1
 * @param firstMinDp    the first region never gets smaller than this
 * @param secondMinDp   the second region never gets smaller than this
 */
public record Split(double firstFraction, double firstMinDp, double secondMinDp) {
    public Split {
        if (!(firstFraction > 0 && firstFraction < 1)) {
            throw new IllegalArgumentException("firstFraction must be between 0 and 1: " + firstFraction);
        }
        Breakpoints.requireSize("firstMinDp", firstMinDp);
        Breakpoints.requireSize("secondMinDp", secondMinDp);
    }

    public static Split even(double minDp) {
        return new Split(0.5, minDp, minDp);
    }

    /**
     * Sizes of both regions for {@code totalDp} of space: the fraction, pushed back inside the minimums when
     * possible. Used by adapters that size regions themselves, and by tests.
     */
    public double[] sizes(double totalDp) {
        Breakpoints.requireSize("totalDp", totalDp);
        double first = totalDp * firstFraction;
        if (totalDp >= firstMinDp + secondMinDp) {
            first = Math.max(firstMinDp, Math.min(first, totalDp - secondMinDp));
        }
        return new double[] {first, totalDp - first};
    }
}
