package io.github.mralireza82.wirelens.inspection.application.statistics;

import io.github.mralireza82.wirelens.relay.domain.traffic.Direction;
import io.github.mralireza82.wirelens.relay.domain.traffic.Side;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static io.github.mralireza82.wirelens.inspection.application.support.Events.closed;
import static io.github.mralireza82.wirelens.inspection.application.support.Events.data;
import static io.github.mralireza82.wirelens.inspection.application.support.Events.opened;
import static org.assertj.core.api.Assertions.assertThat;

class TrafficStatisticsTest {
    private final TrafficStatistics statistics =
            new TrafficStatistics(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void countsBytesPerSideAndDirection() {
        statistics.publish(data(1, Side.SOURCE, Direction.INBOUND, new byte[10]));
        statistics.publish(data(1, Side.DESTINATION, Direction.OUTBOUND, new byte[10]));
        statistics.publish(data(1, Side.DESTINATION, Direction.INBOUND, new byte[3]));
        statistics.publish(data(1, Side.SOURCE, Direction.OUTBOUND, new byte[3]));

        StatisticsSnapshot snapshot = statistics.snapshot();
        assertThat(snapshot.bytesFromClients()).isEqualTo(10);
        assertThat(snapshot.bytesToDestination()).isEqualTo(10);
        assertThat(snapshot.bytesFromDestination()).isEqualTo(3);
        assertThat(snapshot.bytesToClients()).isEqualTo(3);
        assertThat(snapshot.totalBytesReceived()).isEqualTo(13);
        assertThat(snapshot.totalBytesSent()).isEqualTo(13);
        assertThat(snapshot.dataEvents()).isEqualTo(4);
    }

    @Test
    void tracksActiveAndPeakConnectionsFromTheSourceSide() {
        statistics.publish(opened(1, Side.SOURCE));
        statistics.publish(opened(1, Side.DESTINATION));
        statistics.publish(opened(2, Side.SOURCE));
        statistics.publish(closed(1, Side.SOURCE));
        statistics.publish(closed(1, Side.DESTINATION));

        StatisticsSnapshot snapshot = statistics.snapshot();
        assertThat(snapshot.activeConnections()).isEqualTo(1);
        assertThat(snapshot.peakConnections()).isEqualTo(2);
        assertThat(snapshot.totalConnections()).isEqualTo(2);

        statistics.resetTotals();
        assertThat(statistics.snapshot().totalConnections()).isZero();
        assertThat(statistics.snapshot().activeConnections()).isEqualTo(1);
    }

    @Test
    void computesRates() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        StatisticsSnapshot before = StatisticsSnapshot.empty(start);
        StatisticsSnapshot after = new StatisticsSnapshot(start.plus(Duration.ofMillis(500)), 1000, 1000, 250, 250,
                0, 0, 0, 0, 0);
        assertThat(after.upstreamRate(before)).isEqualTo(2000.0);
        assertThat(after.downstreamRate(before)).isEqualTo(500.0);
        assertThat(before.upstreamRate(after)).isZero();
    }
}
