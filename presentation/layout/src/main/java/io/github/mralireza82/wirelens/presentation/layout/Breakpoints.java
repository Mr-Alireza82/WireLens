package io.github.mralireza82.wirelens.presentation.layout;

/** Shared checks for the breakpoint enums. */
final class Breakpoints {
    private Breakpoints() {
    }

    static void requireSize(String name, double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be a finite, non-negative size in dp: " + value);
        }
    }
}
