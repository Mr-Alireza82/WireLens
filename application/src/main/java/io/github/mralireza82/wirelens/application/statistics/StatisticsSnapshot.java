package io.github.mralireza82.wirelens.application.statistics;

import java.time.Duration;
import java.time.Instant;

/**
 * Counters at one moment. "Upstream" is client to destination, "downstream" is destination to client.
 *
 * @param bytesFromClients     received on the source side
 * @param bytesToDestination   sent on the destination side
 * @param bytesFromDestination received on the destination side
 * @param bytesToClients       sent on the source side
 */
public record StatisticsSnapshot(Instant takenAt, long bytesFromClients, long bytesToDestination,
                                 long bytesFromDestination, long bytesToClients, int activeConnections,
                                 int peakConnections, long totalConnections, long failures, long dataEvents) {

    public static StatisticsSnapshot empty(Instant at) {
        return new StatisticsSnapshot(at, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public long totalBytesReceived() {
        return bytesFromClients + bytesFromDestination;
    }

    public long totalBytesSent() {
        return bytesToDestination + bytesToClients;
    }

    /** Client-to-destination bytes per second since {@code earlier}. */
    public double upstreamRate(StatisticsSnapshot earlier) {
        return rate(bytesFromClients - earlier.bytesFromClients, earlier);
    }

    /** Destination-to-client bytes per second since {@code earlier}. */
    public double downstreamRate(StatisticsSnapshot earlier) {
        return rate(bytesFromDestination - earlier.bytesFromDestination, earlier);
    }

    private double rate(long delta, StatisticsSnapshot earlier) {
        double seconds = Duration.between(earlier.takenAt, takenAt).toNanos() / 1e9;
        return seconds <= 0 || delta < 0 ? 0 : delta / seconds;
    }
}
