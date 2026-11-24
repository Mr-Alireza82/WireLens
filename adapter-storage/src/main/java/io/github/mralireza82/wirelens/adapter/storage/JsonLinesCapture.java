package io.github.mralireza82.wirelens.adapter.storage;

import io.github.mralireza82.baseconversion.ByteEncoding;
import io.github.mralireza82.wirelens.application.port.out.CaptureExporter;
import io.github.mralireza82.wirelens.application.port.out.CaptureImporter;
import io.github.mralireza82.wirelens.domain.net.Endpoint;
import io.github.mralireza82.wirelens.domain.traffic.ConnectionId;
import io.github.mralireza82.wirelens.domain.traffic.DataOrigin;
import io.github.mralireza82.wirelens.domain.traffic.Direction;
import io.github.mralireza82.wirelens.domain.traffic.FailureStage;
import io.github.mralireza82.wirelens.domain.traffic.Link;
import io.github.mralireza82.wirelens.domain.traffic.Payload;
import io.github.mralireza82.wirelens.domain.traffic.Side;
import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The native capture format: one JSON object per line, raw bytes as upper-case hex. Lossless (every field and
 * byte round-trips), streamable, greppable and easy to process with {@code jq} or a script.
 *
 * <pre>{"v":1,"time":"2026-11-24T10:15:30.123Z","kind":"DATA","side":"SOURCE","connection":3,
 *  "local":"127.0.0.1:9000","remote":"10.0.0.7:50123","direction":"INBOUND","origin":"RELAYED","size":2,
 *  "hex":"008E"}</pre>
 */
public final class JsonLinesCapture implements CaptureExporter, CaptureImporter {
    private static final int FORMAT_VERSION = 1;

    @Override
    public String displayName() {
        return "WireLens capture (JSON Lines)";
    }

    @Override
    public String fileExtension() {
        return "jsonl";
    }

    @Override
    public void export(List<? extends TrafficEvent> events, Path target) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            for (TrafficEvent event : events) {
                writer.write(toLine(event));
                writer.newLine();
            }
        }
        Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public List<TrafficEvent> load(Path source) throws IOException {
        List<TrafficEvent> events = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    events.add(fromLine(line));
                } catch (RuntimeException e) {
                    throw new IOException("Line " + number + " of " + source.getFileName() + " is not a WireLens event: "
                            + e.getMessage(), e);
                }
            }
        }
        return events;
    }

    static String toLine(TrafficEvent event) {
        Json.ObjectWriter json = new Json.ObjectWriter()
                .put("v", FORMAT_VERSION)
                .put("time", event.timestamp().toString())
                .put("kind", event.kind().name())
                .put("side", event.side().name());
        event.connectionId().ifPresent(id -> json.put("connection", id.value()));
        switch (event) {
            case TrafficEvent.ConnectionOpened opened -> link(json, opened.link());
            case TrafficEvent.DataTransferred data -> {
                link(json, data.link());
                json.put("direction", data.direction().name())
                        .put("origin", data.origin().name())
                        .put("size", data.payload().size())
                        .put("hex", ByteEncoding.toHex(data.payload().toByteArray()));
            }
            case TrafficEvent.StreamEnded ended -> {
                link(json, ended.link());
                json.put("direction", ended.direction().name());
            }
            case TrafficEvent.ConnectionClosed closed -> {
                link(json, closed.link());
                json.put("bytesIn", closed.bytesIn())
                        .put("bytesOut", closed.bytesOut())
                        .put("durationMillis", closed.duration().toMillis())
                        .put("reason", closed.reason());
            }
            case TrafficEvent.ConnectionFailed failed -> {
                failed.remote().ifPresent(remote -> json.put("remote", remote.toString()));
                json.put("stage", failed.stage().name()).put("message", failed.message());
            }
            case TrafficEvent.Notice notice -> json.put("message", notice.message());
        }
        return json.build();
    }

    private static void link(Json.ObjectWriter json, Link link) {
        json.put("local", link.local().toString()).put("remote", link.remote().toString());
    }

    static TrafficEvent fromLine(String line) {
        Map<String, Object> values = Json.parseObject(line);
        long version = number(values, "v");
        if (version != FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported format version " + version);
        }
        Instant time = Instant.parse(text(values, "time"));
        Side side = Side.valueOf(text(values, "side"));
        String kind = text(values, "kind");
        return switch (kind) {
            case "OPENED" -> new TrafficEvent.ConnectionOpened(time, link(values, side));
            case "DATA" -> {
                byte[] bytes = ByteEncoding.fromHex(text(values, "hex"));
                if (bytes.length != number(values, "size")) {
                    throw new IllegalArgumentException("size does not match the hex data");
                }
                yield new TrafficEvent.DataTransferred(time, link(values, side),
                        Direction.valueOf(text(values, "direction")), Payload.copyOf(bytes),
                        DataOrigin.valueOf(text(values, "origin")));
            }
            case "STREAM_ENDED" -> new TrafficEvent.StreamEnded(time, link(values, side),
                    Direction.valueOf(text(values, "direction")));
            case "CLOSED" -> new TrafficEvent.ConnectionClosed(time, link(values, side), number(values, "bytesIn"),
                    number(values, "bytesOut"), Duration.ofMillis(number(values, "durationMillis")),
                    text(values, "reason"));
            case "FAILED" -> new TrafficEvent.ConnectionFailed(time, side,
                    optionalNumber(values, "connection").map(ConnectionId::new),
                    Optional.ofNullable((String) values.get("remote")).map(Endpoint::parse),
                    FailureStage.valueOf(text(values, "stage")), text(values, "message"));
            case "NOTICE" -> new TrafficEvent.Notice(time, side, text(values, "message"));
            default -> throw new IllegalArgumentException("unknown kind " + kind);
        };
    }

    private static Link link(Map<String, Object> values, Side side) {
        return new Link(new ConnectionId(number(values, "connection")), side, Endpoint.parse(text(values, "local")),
                Endpoint.parse(text(values, "remote")));
    }

    private static String text(Map<String, Object> values, String key) {
        if (!(values.get(key) instanceof String value)) {
            throw new IllegalArgumentException("missing text field '" + key + "'");
        }
        return value;
    }

    private static long number(Map<String, Object> values, String key) {
        return optionalNumber(values, key).orElseThrow(() ->
                new IllegalArgumentException("missing number field '" + key + "'"));
    }

    private static Optional<Long> optionalNumber(Map<String, Object> values, String key) {
        return values.get(key) instanceof Long value ? Optional.of(value) : Optional.empty();
    }
}
