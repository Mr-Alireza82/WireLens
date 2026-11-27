package io.github.mralireza82.wirelens.adapter.javafx.viewmodel;

import io.github.mralireza82.wirelens.domain.traffic.Direction;
import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;

import java.util.Comparator;
import java.util.Optional;

/**
 * How a panel orders its entries. {@link #LIVE} keeps arrival order and follows new entries; the others are
 * snapshots that re-sort as entries arrive. Ties always fall back to arrival order, newest first, so the order is
 * stable and predictable.
 */
public enum SortOrder {
    LIVE("Live", "Arrival order, follows new entries", null),
    NEWEST_FIRST("Newest first", "Most recent entry on top", newestFirst()),
    OLDEST_FIRST("Oldest first", "Arrival order, without following", Comparator.comparingLong(TrafficEntry::number)),
    LARGEST_FIRST("Largest first", "Biggest payloads on top", Comparator.comparingInt(SortOrder::size).reversed()
            .thenComparing(newestFirst())),
    SMALLEST_FIRST("Smallest first", "Smallest payloads on top, events without data last",
            Comparator.comparingInt(SortOrder::sizeOrMax).thenComparing(newestFirst())),
    BY_CONNECTION("By connection", "Grouped by connection, each in arrival order",
            Comparator.comparingLong(SortOrder::connection).thenComparingLong(TrafficEntry::number)),
    RECEIVED_FIRST("Received first", "Data received from the peer, newest first, then the rest",
            Comparator.comparingInt((TrafficEntry e) -> rank(e, Direction.INBOUND)).thenComparing(newestFirst())),
    SENT_FIRST("Sent first", "Data sent to the peer, newest first, then the rest",
            Comparator.comparingInt((TrafficEntry e) -> rank(e, Direction.OUTBOUND)).thenComparing(newestFirst()));

    private final String title;
    private final String description;
    private final Comparator<TrafficEntry> comparator;

    SortOrder(String title, String description, Comparator<TrafficEntry> comparator) {
        this.title = title;
        this.description = description;
        this.comparator = comparator;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    /** Empty for {@link #LIVE}, which keeps the order entries arrived in. */
    public Optional<Comparator<TrafficEntry>> comparator() {
        return Optional.ofNullable(comparator);
    }

    private static Comparator<TrafficEntry> newestFirst() {
        return Comparator.comparingLong(TrafficEntry::number).reversed();
    }

    private static int size(TrafficEntry entry) {
        return entry.event().byteCount();
    }

    private static int sizeOrMax(TrafficEntry entry) {
        return entry.event() instanceof TrafficEvent.DataTransferred ? size(entry) : Integer.MAX_VALUE;
    }

    private static long connection(TrafficEntry entry) {
        return entry.event().connectionId().map(id -> id.value()).orElse(Long.MAX_VALUE);
    }

    private static int rank(TrafficEntry entry, Direction wanted) {
        return entry.event() instanceof TrafficEvent.DataTransferred data && data.direction() == wanted ? 0 : 1;
    }
}
