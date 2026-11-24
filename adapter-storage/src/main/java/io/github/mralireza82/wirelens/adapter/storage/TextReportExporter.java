package io.github.mralireza82.wirelens.adapter.storage;

import io.github.mralireza82.wirelens.application.port.out.CaptureExporter;
import io.github.mralireza82.wirelens.application.render.PayloadRenderer;
import io.github.mralireza82.wirelens.application.render.TextEncoding;
import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** A human-readable report with a hex dump per data event, handy for tickets and code reviews. */
public final class TextReportExporter implements CaptureExporter {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final PayloadRenderer renderer = new PayloadRenderer();
    private final TextEncoding encoding;

    public TextReportExporter(TextEncoding encoding) {
        this.encoding = encoding;
    }

    @Override
    public String displayName() {
        return "Text report with hex dumps";
    }

    @Override
    public String fileExtension() {
        return "txt";
    }

    @Override
    public void export(List<? extends TrafficEvent> events, Path target) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write("WireLens capture report: " + events.size() + " events, text decoded as "
                    + encoding.displayName());
            writer.newLine();
            writer.newLine();
            for (TrafficEvent event : events) {
                writer.write(headline(event));
                writer.newLine();
                if (event instanceof TrafficEvent.DataTransferred data) {
                    writer.write(renderer.hexDump(data.payload(), encoding));
                }
                writer.newLine();
            }
        }
    }

    private static String headline(TrafficEvent event) {
        String connection = event.connectionId().map(id -> id + " ").orElse("");
        String remote = event.remote().map(r -> " " + r).orElse("");
        String detail = switch (event) {
            case TrafficEvent.ConnectionOpened ignored -> "connection opened";
            case TrafficEvent.DataTransferred data -> (data.direction().name().equals("INBOUND") ? "received " : "sent ")
                    + data.payload().size() + " bytes" + (data.origin().name().equals("INJECTED") ? " (typed by user)" : "");
            case TrafficEvent.StreamEnded ended -> "end of stream " + ended.direction().name().toLowerCase();
            case TrafficEvent.ConnectionClosed closed -> "closed (" + closed.reason() + "), in " + closed.bytesIn()
                    + " B, out " + closed.bytesOut() + " B";
            case TrafficEvent.ConnectionFailed failed -> failed.stage() + " failed: " + failed.message();
            case TrafficEvent.Notice notice -> notice.message();
        };
        return TIME.format(event.timestamp()) + "  " + event.side() + "  " + connection + detail + remote;
    }
}
