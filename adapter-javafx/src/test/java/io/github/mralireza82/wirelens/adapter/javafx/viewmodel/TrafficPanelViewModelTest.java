package io.github.mralireza82.wirelens.adapter.javafx.viewmodel;

import io.github.mralireza82.wirelens.application.port.in.ConnectionCommands;
import io.github.mralireza82.wirelens.application.render.Radix;
import io.github.mralireza82.wirelens.application.render.RenderSettings;
import io.github.mralireza82.wirelens.domain.net.Endpoint;
import io.github.mralireza82.wirelens.domain.traffic.ConnectionId;
import io.github.mralireza82.wirelens.domain.traffic.DataOrigin;
import io.github.mralireza82.wirelens.domain.traffic.Direction;
import io.github.mralireza82.wirelens.domain.traffic.EventKind;
import io.github.mralireza82.wirelens.domain.traffic.Link;
import io.github.mralireza82.wirelens.domain.traffic.Payload;
import io.github.mralireza82.wirelens.domain.traffic.Side;
import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import io.github.mralireza82.wirelens.application.intercept.HoldService;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs without a JavaFX toolkit: properties, bindings and FilteredList work headless. */
class TrafficPanelViewModelTest {
    private final ObservableSet<ConnectionId> open = FXCollections.observableSet();
    private final List<String> sent = new ArrayList<>();
    private final List<String> notifications = new ArrayList<>();
    private final ConnectionCommands commands = new ConnectionCommands() {
        @Override
        public void send(ConnectionId connection, Side side, Payload payload) throws IOException {
            if (connection.value() == 13) {
                throw new IOException("Broken pipe");
            }
            sent.add(connection + " " + side + " " + new String(payload.toByteArray(), StandardCharsets.ISO_8859_1));
        }

        @Override
        public void disconnect(ConnectionId connection) {
            sent.add("disconnect " + connection);
        }

        @Override
        public Set<ConnectionId> openConnections() {
            return open;
        }
    };
    private final TrafficPanelViewModel panel = new TrafficPanelViewModel(Side.SOURCE,
            new EndpointInput("127.0.0.1", "9000"), RenderSettings.DEFAULT, 1000, 10_000, commands,
            new HoldService(event -> { }, (target, payload, timeout) -> Payload.empty(), Clock.systemUTC()), open,
            Runnable::run, Runnable::run, (message, error) -> notifications.add((error ? "ERR " : "") + message));

    private static TrafficEvent data(long id, Side side, String text) {
        Link link = new Link(new ConnectionId(id), side, Endpoint.of("127.0.0.1", 9000), Endpoint.of("10.0.0.2", 5000));
        return new TrafficEvent.DataTransferred(Instant.now(), link, Direction.INBOUND,
                Payload.copyOf(text.getBytes(StandardCharsets.ISO_8859_1)), DataOrigin.RELAYED);
    }

    @Test
    void keepsOnlyItsOwnSide() {
        panel.accept(List.of(data(1, Side.SOURCE, "a"), data(1, Side.DESTINATION, "b"), data(2, Side.SOURCE, "c")));
        assertThat(panel.entries()).extracting(TrafficEntry::number).containsExactly(1L, 2L);
    }

    @Test
    void boundsTheHistoryAndDropsTheOldest() {
        List<TrafficEvent> batch = new ArrayList<>();
        for (int i = 0; i < 2500; i++) {
            batch.add(data(1, Side.SOURCE, "x" + i));
        }
        panel.accept(batch.subList(0, 700));
        panel.accept(batch.subList(700, 2500));
        assertThat(panel.entries()).hasSize(1000);
        assertThat(panel.entries().getFirst().number()).isEqualTo(1501);
        assertThat(panel.entries().getLast().number()).isEqualTo(2500);
    }

    @Test
    void boundsTheRetainedBytes() {
        String kilobyte = "k".repeat(1024);
        List<TrafficEvent> batch = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            batch.add(data(1, Side.SOURCE, kilobyte));
        }
        panel.accept(batch.subList(0, 5));
        assertThat(panel.retainedBytes()).isEqualTo(5 * 1024);
        panel.accept(batch.subList(5, 25));
        assertThat(panel.retainedBytes()).isLessThanOrEqualTo(10_000);
        assertThat(panel.entries()).hasSize(9);
        assertThat(panel.entries().getLast().number()).isEqualTo(25);
        panel.clear();
        assertThat(panel.retainedBytes()).isZero();
    }

    @Test
    void pausingSkipsButCounts() {
        panel.togglePause();
        panel.accept(List.of(data(1, Side.SOURCE, "a"), data(1, Side.SOURCE, "b")));
        assertThat(panel.entries()).isEmpty();
        assertThat(panel.skippedWhilePaused().get()).isEqualTo(2);
        panel.togglePause();
        assertThat(panel.skippedWhilePaused().get()).isZero();
        panel.accept(List.of(data(1, Side.SOURCE, "c")));
        assertThat(panel.entries()).hasSize(1);
    }

    @Test
    void filtersBySearchAndKind() {
        panel.accept(List.of(data(1, Side.SOURCE, "hello"), data(2, Side.SOURCE, "world"),
                new TrafficEvent.Notice(Instant.now(), Side.SOURCE, "Listening")));
        panel.query().set("HELLO");
        assertThat(panel.visibleEntries()).hasSize(1);
        panel.query().set("");
        panel.kinds().set(EnumSet.of(EventKind.NOTICE));
        assertThat(panel.visibleEvents()).singleElement().isInstanceOf(TrafficEvent.Notice.class);
        panel.focusConnection(new ConnectionId(2));
        panel.kinds().set(EnumSet.allOf(EventKind.class));
        assertThat(panel.visibleEntries()).hasSize(1);
    }

    @Test
    void rendersWithTheCurrentSettingsWithoutTouchingTheEvent() {
        panel.accept(List.of(data(1, Side.SOURCE, "\u0000\u008E")));
        TrafficEntry entry = panel.entries().getFirst();
        var renderer = new io.github.mralireza82.wirelens.application.render.PayloadRenderer();
        assertThat(entry.previewNumbers(renderer, panel.settings().get(), 64)).isEqualTo("00 8E");
        panel.settings().set(panel.settings().get().withRadix(Radix.BINARY).withSeparator("-"));
        assertThat(entry.previewNumbers(renderer, panel.settings().get(), 64)).isEqualTo("00000000-10001110");
        assertThat(entry.capturedWith()).isEqualTo(RenderSettings.DEFAULT);
        assertThat(((TrafficEvent.DataTransferred) entry.event()).payload().toByteArray()).containsExactly(0, 0x8E);
    }

    @Test
    void sendsOnlyToAnOpenTargetConnection() {
        panel.composerInput().set("02 41 03");
        assertThat(panel.canSend().get()).isFalse(); // no target

        ConnectionSummary seven = new ConnectionSummary(new ConnectionId(7), Endpoint.of("10.0.0.2", 5000),
                Endpoint.of("127.0.0.1", 9000), Optional.empty(), Instant.now(), true);
        panel.target().set(seven);
        assertThat(panel.canSend().get()).isFalse(); // connection not open
        open.add(new ConnectionId(7));
        assertThat(panel.composerTarget().get()).contains(new ConnectionId(7));
        assertThat(panel.canSend().get()).isTrue();

        panel.send();
        panel.disconnectTarget();
        assertThat(sent).containsExactly("#7 SOURCE \u0002A\u0003", "disconnect #7");
        assertThat(notifications).containsExactly("Sent 3 bytes to the client of #7");
    }

    @Test
    void sortsWithoutLosingTheFilter() {
        panel.accept(List.of(data(1, Side.SOURCE, "aaaa"), data(2, Side.SOURCE, "b"), data(1, Side.SOURCE, "cc")));
        panel.sortOrder().set(SortOrder.LARGEST_FIRST);
        assertThat(panel.displayedEntries()).extracting(TrafficEntry::number).containsExactly(1L, 3L, 2L);
        assertThat(panel.followLatest().get()).isFalse();
        panel.sortOrder().set(SortOrder.BY_CONNECTION);
        assertThat(panel.displayedEntries()).extracting(TrafficEntry::number).containsExactly(1L, 3L, 2L);
        panel.sortOrder().set(SortOrder.NEWEST_FIRST);
        panel.query().set("#1");
        assertThat(panel.displayedEntries()).extracting(TrafficEntry::number).containsExactly(3L, 1L);
        panel.sortOrder().set(SortOrder.LIVE);
        assertThat(panel.displayedEntries()).extracting(TrafficEntry::number).containsExactly(1L, 3L);
        assertThat(panel.followLatest().get()).isTrue();
    }

    @Test
    void validatesTheSendBoxLive() {
        panel.composerInput().set("02 4G");
        assertThat(panel.composerError().get()).contains("'G' is not a hexadecimal digit");
        panel.composerMode().set(ComposerMode.TEXT);
        assertThat(panel.composerError().get()).isEmpty();
        panel.composerInput().set("bad \\q");
        assertThat(panel.composerError().get()).contains("Unknown escape");
    }

    @Test
    void reportsSendFailures() {
        open.add(new ConnectionId(13));
        panel.target().set(new ConnectionSummary(new ConnectionId(13), Endpoint.of("10.0.0.2", 5000),
                Endpoint.of("127.0.0.1", 9000), Optional.empty(), Instant.now(), true));
        panel.composerMode().set(ComposerMode.TEXT);
        panel.composerInput().set("ping");
        panel.send();
        assertThat(notifications).containsExactly("ERR Could not send to #13: Broken pipe");
    }

    @Test
    void validatesEndpoints() {
        EndpointInput input = new EndpointInput("10.0.0.1", "8583");
        assertThat(input.valid().get()).isTrue();
        assertThat(input.endpoint()).contains(Endpoint.of("10.0.0.1", 8583));
        input.address().set("10.0.0.256");
        assertThat(input.valid().get()).isFalse();
        assertThat(input.addressError().get()).contains("out of range");
        assertThat(input.endpoint()).isEmpty();
        input.address().set("2001:db8::1");
        input.port().set("70000");
        assertThat(input.portError().get()).contains("65535");
        input.port().set("443");
        assertThat(input.endpoint().orElseThrow()).hasToString("[2001:db8::1]:443");
    }
}
