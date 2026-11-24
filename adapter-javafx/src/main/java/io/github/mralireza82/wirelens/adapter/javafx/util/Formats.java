package io.github.mralireza82.wirelens.adapter.javafx.util;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Human-friendly formatting for sizes, rates, durations and times. */
public final class Formats {
    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private Formats() {
    }

    /** {@code 512 B}, {@code 1.5 KB}, {@code 12.3 MB} (binary multiples). */
    public static String bytes(long count) {
        if (count < 1024) {
            return count + " B";
        }
        double value = count;
        int unit = 0;
        while (value >= 1024 && unit < UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, value >= 100 ? "%.0f %s" : "%.1f %s", value, UNITS[unit]);
    }

    public static String rate(double bytesPerSecond) {
        return bytes(Math.round(bytesPerSecond)) + "/s";
    }

    /** {@code 12,345}. */
    public static String count(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** {@code 03:25} or {@code 1:02:03}. */
    public static String duration(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long rest = seconds % 60;
        return hours > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, rest);
    }

    /** Local time with milliseconds, {@code 14:03:27.415}. */
    public static String time(Instant instant) {
        return TIME.format(instant);
    }

    public static String dateTime(Instant instant) {
        return DATE_TIME.format(instant);
    }
}
