package io.github.mralireza82.wirelens.application.filter;

import io.github.mralireza82.wirelens.application.render.TextEncoding;
import io.github.mralireza82.wirelens.domain.traffic.Direction;
import io.github.mralireza82.wirelens.domain.traffic.EventKind;
import io.github.mralireza82.wirelens.domain.traffic.Side;
import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static io.github.mralireza82.wirelens.application.support.Events.T0;
import static io.github.mralireza82.wirelens.application.support.Events.data;
import static io.github.mralireza82.wirelens.application.support.Events.opened;
import static org.assertj.core.api.Assertions.assertThat;

class TrafficFilterTest {
    private final TrafficEvent.DataTransferred hello =
            data(3, Side.SOURCE, Direction.INBOUND, "\u0002\nHello".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

    private static TrafficFilter query(String text) {
        return new TrafficFilter(text, EnumSet.allOf(EventKind.class), TextEncoding.ASCII);
    }

    @Test
    void emptyQueryMatchesEverything() {
        assertThat(TrafficFilter.all(TextEncoding.ASCII).test(hello)).isTrue();
        assertThat(TrafficFilter.all(TextEncoding.ASCII).isUnrestricted()).isTrue();
    }

    @Test
    void matchesDecodedTextCaseInsensitively() {
        assertThat(query("HELLO").test(hello)).isTrue();
        assertThat(query("bye").test(hello)).isFalse();
    }

    @Test
    void matchesRawBytesOnByteBoundaries() {
        assertThat(query("02 0a").test(hello)).isTrue();
        assertThat(query("0x020A48").test(hello)).isTrue();
        assertThat(query("20").test(hello)).isFalse(); // 02 0A contains "20" only across a byte boundary
    }

    @Test
    void matchesConnectionPeerAndKind() {
        assertThat(query("#3").test(hello)).isTrue();
        assertThat(query("10.1.1.7").test(hello)).isTrue();
        assertThat(query("opened").test(opened(1, Side.SOURCE))).isTrue();
        assertThat(query("refused").test(new TrafficEvent.ConnectionFailed(T0, Side.DESTINATION,
                java.util.Optional.empty(), java.util.Optional.empty(),
                io.github.mralireza82.wirelens.domain.traffic.FailureStage.CONNECT, "Connection refused"))).isTrue();
    }

    @Test
    void filtersByKind() {
        TrafficFilter onlyConnections = new TrafficFilter("", EnumSet.of(EventKind.OPENED), TextEncoding.ASCII);
        assertThat(onlyConnections.test(hello)).isFalse();
        assertThat(onlyConnections.test(opened(1, Side.SOURCE))).isTrue();
        assertThat(onlyConnections.isUnrestricted()).isFalse();
    }
}
