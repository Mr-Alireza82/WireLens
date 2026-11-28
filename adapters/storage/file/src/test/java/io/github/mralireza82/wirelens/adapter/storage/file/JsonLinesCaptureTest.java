package io.github.mralireza82.wirelens.adapter.storage.file;

import io.github.mralireza82.wirelens.inspection.application.render.TextEncoding;
import io.github.mralireza82.wirelens.kernel.net.Endpoint;
import io.github.mralireza82.wirelens.kernel.id.ConnectionId;
import io.github.mralireza82.wirelens.relay.domain.traffic.DataOrigin;
import io.github.mralireza82.wirelens.relay.domain.traffic.Direction;
import io.github.mralireza82.wirelens.relay.domain.traffic.FailureStage;
import io.github.mralireza82.wirelens.relay.domain.traffic.Link;
import io.github.mralireza82.wirelens.kernel.bytes.Payload;
import io.github.mralireza82.wirelens.relay.domain.traffic.Side;
import io.github.mralireza82.wirelens.relay.domain.traffic.TrafficEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonLinesCaptureTest {
    private static final Instant T = Instant.parse("2026-11-24T10:15:30.123456Z");
    private final Link source = new Link(new ConnectionId(3), Side.SOURCE, Endpoint.of("127.0.0.1", 9000),
            Endpoint.of("10.0.0.7", 50123));
    private final Link destination = new Link(new ConnectionId(3), Side.DESTINATION, Endpoint.of("::1", 40000),
            Endpoint.of("2001:db8::10", 8583));
    private final JsonLinesCapture format = new JsonLinesCapture();

    @TempDir
    Path folder;

    private List<TrafficEvent> everyKindOfEvent() {
        byte[] random = new byte[4096];
        new Random(3).nextBytes(random);
        return List.of(
                new TrafficEvent.Notice(T, Side.SOURCE, "Listening on 127.0.0.1:9000 \"quoted\"\n\ttab"),
                new TrafficEvent.ConnectionOpened(T, source),
                new TrafficEvent.ConnectionOpened(T, destination),
                new TrafficEvent.DataTransferred(T, source, Direction.INBOUND, Payload.copyOf(new byte[] {0, (byte) 0x8E}),
                        DataOrigin.RELAYED),
                new TrafficEvent.DataTransferred(T, destination, Direction.OUTBOUND, Payload.copyOf(random),
                        DataOrigin.INJECTED),
                new TrafficEvent.DataTransferred(T, destination, Direction.OUTBOUND, Payload.empty(), DataOrigin.RELAYED),
                new TrafficEvent.StreamEnded(T, source, Direction.INBOUND),
                new TrafficEvent.ConnectionClosed(T, source, 2, 4096, Duration.ofMillis(1234), "client closed"),
                new TrafficEvent.ConnectionFailed(T, Side.DESTINATION, Optional.of(new ConnectionId(4)),
                        Optional.of(Endpoint.of("10.9.9.9", 1)), FailureStage.CONNECT, "Connection refused"),
                new TrafficEvent.ConnectionFailed(T, Side.SOURCE, Optional.empty(), Optional.empty(),
                        FailureStage.LISTEN, "Address already in use \u0001"));
    }

    @Test
    void roundTripsEveryEventLosslessly() throws IOException {
        List<TrafficEvent> events = everyKindOfEvent();
        Path file = folder.resolve("capture.jsonl");

        format.export(events, file);

        assertThat(format.load(file)).isEqualTo(events);
        assertThat(Files.readAllLines(file)).hasSize(events.size());
        assertThat(folder.resolve("capture.jsonl.part")).doesNotExist();
    }

    @Test
    void writesReadableRecords() {
        String line = JsonLinesCapture.toLine(everyKindOfEvent().get(3));
        assertThat(line).isEqualTo("{\"v\":1,\"time\":\"2026-11-24T10:15:30.123456Z\",\"kind\":\"DATA\",\"side\":\"SOURCE\","
                + "\"connection\":3,\"local\":\"127.0.0.1:9000\",\"remote\":\"10.0.0.7:50123\",\"direction\":\"INBOUND\","
                + "\"origin\":\"RELAYED\",\"size\":2,\"hex\":\"008E\"}");
    }

    @Test
    void reportsTheBrokenLine() throws IOException {
        Path file = folder.resolve("broken.jsonl");
        Files.writeString(file, JsonLinesCapture.toLine(everyKindOfEvent().get(0)) + "\n\n{\"v\":1,\"kind\":", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> format.load(file)).isInstanceOf(IOException.class).hasMessageStartingWith("Line 3 of broken.jsonl");
    }

    @Test
    void rejectsTamperedSizes() {
        String line = JsonLinesCapture.toLine(everyKindOfEvent().get(3)).replace("\"size\":2", "\"size\":3");
        assertThatThrownBy(() -> JsonLinesCapture.fromLine(line)).hasMessageContaining("size does not match");
    }

    @Test
    void writesAReadableTextReport() throws IOException {
        Path file = folder.resolve("report.txt");
        new TextReportExporter(TextEncoding.ASCII).export(everyKindOfEvent(), file);
        String report = Files.readString(file);
        assertThat(report).contains("10 events").contains("00000000  00 8E").contains("received 2 bytes")
                .contains("(typed by user)").contains("CONNECT failed: Connection refused");
    }

    @Test
    void savesRawBytesExactly() throws IOException {
        Path file = folder.resolve("payload.bin");
        new RawPayloadWriter().write(Payload.copyOf(new byte[] {0, (byte) 0xFF, 0x7F}), file);
        assertThat(Files.readAllBytes(file)).containsExactly(0, 0xFF, 0x7F);
    }
}
