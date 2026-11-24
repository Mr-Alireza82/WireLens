package io.github.mralireza82.wirelens.adapter.javafx.view;

import io.github.mralireza82.wirelens.adapter.javafx.util.Formats;
import io.github.mralireza82.wirelens.adapter.javafx.viewmodel.TrafficEntry;
import io.github.mralireza82.wirelens.application.render.PayloadRenderer;
import io.github.mralireza82.wirelens.application.render.RenderSettings;
import io.github.mralireza82.wirelens.domain.traffic.DataOrigin;
import io.github.mralireza82.wirelens.domain.traffic.Direction;
import io.github.mralireza82.wirelens.domain.traffic.Side;
import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;
import org.kordamp.ikonli.feather.Feather;

import java.util.List;

/** Words, badges and copy formats for traffic events, shared by the list cells and the inspector. */
final class EntryPresenter {
    record Badge(String text, String styleClass, Feather icon) {
    }

    private EntryPresenter() {
    }

    static Badge badge(TrafficEvent event) {
        return switch (event) {
            case TrafficEvent.DataTransferred data -> data.direction() == Direction.INBOUND
                    ? new Badge("RECEIVED", "in", Feather.ARROW_DOWN_LEFT)
                    : new Badge("SENT", "out", Feather.ARROW_UP_RIGHT);
            case TrafficEvent.ConnectionOpened ignored -> new Badge("CONNECTED", "opened", Feather.LINK);
            case TrafficEvent.StreamEnded ignored -> new Badge("END OF STREAM", "ended", Feather.CORNER_DOWN_RIGHT);
            case TrafficEvent.ConnectionClosed ignored -> new Badge("CLOSED", "closed", Feather.X_CIRCLE);
            case TrafficEvent.ConnectionFailed ignored -> new Badge("ERROR", "failed", Feather.ALERT_TRIANGLE);
            case TrafficEvent.Notice ignored -> new Badge("INFO", "notice", Feather.INFO);
        };
    }

    static boolean isTyped(TrafficEvent event) {
        return event instanceof TrafficEvent.DataTransferred data && data.origin() == DataOrigin.INJECTED;
    }

    /** "client", "destination". */
    static String peerName(Side side) {
        return side == Side.SOURCE ? "client" : "destination";
    }

    /** The muted text after the badge: who the bytes came from or went to. */
    static String peer(TrafficEvent event) {
        return switch (event) {
            case TrafficEvent.DataTransferred data -> (data.direction() == Direction.INBOUND ? "from " : "to ")
                    + data.link().remote();
            case TrafficEvent.ConnectionOpened opened -> opened.side() == Side.SOURCE
                    ? "client " + opened.link().remote() + " → " + opened.link().local()
                    : opened.link().local() + " → destination " + opened.link().remote();
            case TrafficEvent.StreamEnded ended -> ended.link().remote().toString();
            case TrafficEvent.ConnectionClosed closed -> closed.link().remote().toString();
            case TrafficEvent.ConnectionFailed failed -> failed.remote().map(Object::toString).orElse("");
            case TrafficEvent.Notice ignored -> "";
        };
    }

    /** A sentence for events without a payload; empty for data. */
    static String message(TrafficEvent event) {
        return switch (event) {
            case TrafficEvent.DataTransferred ignored -> "";
            case TrafficEvent.ConnectionOpened opened -> opened.side() == Side.SOURCE
                    ? "New client connection accepted" : "Connected to the destination";
            case TrafficEvent.StreamEnded ended -> ended.direction() == Direction.INBOUND
                    ? "The " + peerName(ended.side()) + " finished sending (FIN); the other direction stays open"
                    : "End of stream passed on to the " + peerName(ended.side());
            case TrafficEvent.ConnectionClosed closed -> "Closed: " + closed.reason() + " · received "
                    + Formats.bytes(closed.bytesIn()) + " · sent " + Formats.bytes(closed.bytesOut()) + " · lasted "
                    + seconds(closed.duration());
            case TrafficEvent.ConnectionFailed failed -> failed.stage().name().charAt(0)
                    + failed.stage().name().substring(1).toLowerCase() + " failed: " + failed.message();
            case TrafficEvent.Notice notice -> notice.message();
        };
    }

    private static String seconds(java.time.Duration duration) {
        long millis = duration.toMillis();
        return millis < 1000 ? millis + " ms" : String.format(java.util.Locale.ROOT, "%.1f s", millis / 1000.0);
    }

    /** One line describing the entry, used when copying. */
    static String headline(TrafficEntry entry) {
        TrafficEvent event = entry.event();
        String connection = event.connectionId().map(id -> " " + id).orElse("");
        String size = event instanceof TrafficEvent.DataTransferred data ? " " + data.payload().size() + " B" : "";
        String peer = peer(event);
        return Formats.time(event.timestamp()) + " " + event.side() + " " + badge(event).text()
                + (isTyped(event) ? " (typed)" : "") + connection + (peer.isEmpty() ? "" : " " + peer) + size;
    }

    /** What "Copy entry" puts on the clipboard: headline plus numbers and text, or the message. */
    static String copyText(TrafficEntry entry, PayloadRenderer renderer, RenderSettings settings) {
        StringBuilder text = new StringBuilder(headline(entry));
        if (entry.event() instanceof TrafficEvent.DataTransferred data) {
            text.append('\n').append(settings.radix().shortName()).append(": ")
                    .append(renderer.numbers(data.payload(), settings.withBytesPerLine(0)))
                    .append('\n').append("TEXT: ").append(renderer.text(data.payload(), settings.encoding()));
        } else {
            text.append('\n').append(message(entry.event()));
        }
        return text.toString();
    }

    static String copyText(List<TrafficEntry> entries, PayloadRenderer renderer, RenderSettings settings) {
        StringBuilder text = new StringBuilder();
        for (TrafficEntry entry : entries) {
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append(copyText(entry, renderer, settings));
        }
        return text.toString();
    }
}
