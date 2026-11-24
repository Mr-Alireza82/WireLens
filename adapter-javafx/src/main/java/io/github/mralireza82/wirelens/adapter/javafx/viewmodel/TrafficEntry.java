package io.github.mralireza82.wirelens.adapter.javafx.viewmodel;

import io.github.mralireza82.wirelens.application.render.PayloadRenderer;
import io.github.mralireza82.wirelens.application.render.RenderSettings;
import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;

import java.util.Objects;

/**
 * One row of a traffic panel: the immutable event (raw bytes included) plus a small render cache.
 *
 * <p>The cache only remembers the last preview strings for the last settings, so scrolling does not re-render
 * rows, and a selector change invalidates it naturally. It is touched only on the FX thread.
 *
 * @param number      position in the panel's capture, starting at 1
 * @param capturedWith the panel's view settings when the event was captured, so the original presentation can
 *                    always be reproduced, while the stored data stays raw bytes only
 */
public final class TrafficEntry {
    private final long number;
    private final TrafficEvent event;
    private final RenderSettings capturedWith;

    private RenderSettings cachedFor;
    private int cachedLimit;
    private String cachedNumbers;
    private String cachedText;

    public TrafficEntry(long number, TrafficEvent event, RenderSettings capturedWith) {
        this.number = number;
        this.event = Objects.requireNonNull(event, "event");
        this.capturedWith = Objects.requireNonNull(capturedWith, "capturedWith");
    }

    public long number() {
        return number;
    }

    public TrafficEvent event() {
        return event;
    }

    public RenderSettings capturedWith() {
        return capturedWith;
    }

    /** Preview of the numbers view: at most {@code maxBytes} bytes. Empty for non-data events. */
    public String previewNumbers(PayloadRenderer renderer, RenderSettings settings, int maxBytes) {
        fill(renderer, settings, maxBytes);
        return cachedNumbers;
    }

    /** Preview of the text view: at most {@code maxBytes} bytes. Empty for non-data events. */
    public String previewText(PayloadRenderer renderer, RenderSettings settings, int maxBytes) {
        fill(renderer, settings, maxBytes);
        return cachedText;
    }

    private void fill(PayloadRenderer renderer, RenderSettings settings, int maxBytes) {
        if (settings.equals(cachedFor) && maxBytes == cachedLimit) {
            return;
        }
        if (event instanceof TrafficEvent.DataTransferred data) {
            RenderSettings flowing = settings.withBytesPerLine(0);
            cachedNumbers = renderer.numbers(data.payload(), flowing, maxBytes);
            cachedText = renderer.text(data.payload(), settings.encoding(), maxBytes);
        } else {
            cachedNumbers = "";
            cachedText = "";
        }
        cachedFor = settings;
        cachedLimit = maxBytes;
    }
}
