package io.github.mralireza82.wirelens.application.port.out;

import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Writes captured events to a file. Every exporter must keep the raw bytes, so nothing is lost. */
public interface CaptureExporter extends CaptureFormat {
    void export(List<? extends TrafficEvent> events, Path target) throws IOException;
}
