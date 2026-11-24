package io.github.mralireza82.wirelens.application.filter;

import io.github.mralireza82.baseconversion.ByteEncoding;
import io.github.mralireza82.wirelens.application.render.TextEncoding;
import io.github.mralireza82.wirelens.domain.traffic.EventKind;
import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Decides which events a panel shows (Specification). An event matches when its kind is selected and the query
 * (case-insensitive) appears in its connection id, peer address, message, decoded text, or, when the query looks
 * like hex such as {@code 02 00} or {@code 0x0200}, in its raw bytes.
 */
public record TrafficFilter(String query, Set<EventKind> kinds, TextEncoding encoding)
        implements Predicate<TrafficEvent> {
    private static final Pattern HEX_NOISE = Pattern.compile("0x|[\\s,;:_\\-]", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX = Pattern.compile("(?:[0-9a-fA-F]{2})+");

    public TrafficFilter {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(encoding, "encoding");
        kinds = kinds.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(kinds));
    }

    public static TrafficFilter all(TextEncoding encoding) {
        return new TrafficFilter("", EnumSet.allOf(EventKind.class), encoding);
    }

    public boolean isUnrestricted() {
        return query.isBlank() && kinds.size() == EventKind.values().length;
    }

    @Override
    public boolean test(TrafficEvent event) {
        if (!kinds.contains(event.kind())) {
            return false;
        }
        String needle = query.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return true;
        }
        if (event.connectionId().map(id -> id.toString().contains(needle)).orElse(false)
                || event.remote().map(remote -> remote.toString().toLowerCase(Locale.ROOT).contains(needle))
                .orElse(false)
                || event.kind().name().toLowerCase(Locale.ROOT).replace('_', ' ').contains(needle)) {
            return true;
        }
        return switch (event) {
            case TrafficEvent.DataTransferred data -> matchesPayload(data, needle);
            case TrafficEvent.ConnectionFailed failed -> failed.message().toLowerCase(Locale.ROOT).contains(needle);
            case TrafficEvent.Notice notice -> notice.message().toLowerCase(Locale.ROOT).contains(needle);
            case TrafficEvent.ConnectionClosed closed -> closed.reason().toLowerCase(Locale.ROOT).contains(needle);
            case TrafficEvent.ConnectionOpened ignored -> false;
            case TrafficEvent.StreamEnded ignored -> false;
        };
    }

    private boolean matchesPayload(TrafficEvent.DataTransferred data, String needle) {
        byte[] bytes = data.payload().toByteArray();
        if (encoding.decode(bytes).toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        String hexNeedle = HEX_NOISE.matcher(needle).replaceAll("");
        return HEX.matcher(hexNeedle).matches()
                && containsAligned(ByteEncoding.toHex(bytes), hexNeedle.toUpperCase(Locale.ROOT));
    }

    /** Hex matches must start on a byte boundary, so "20" does not match the bytes 02 0A. */
    private static boolean containsAligned(String haystack, String needle) {
        for (int from = haystack.indexOf(needle); from >= 0; from = haystack.indexOf(needle, from + 1)) {
            if (from % 2 == 0) {
                return true;
            }
        }
        return false;
    }
}
