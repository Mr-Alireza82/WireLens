package io.github.mralireza82.wirelens.inspection.application.port.out;

import io.github.mralireza82.wirelens.relay.domain.traffic.TrafficEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Reads a capture written by the matching {@link CaptureExporter} back into events. */
public interface CaptureImporter extends CaptureFormat {
    List<TrafficEvent> load(Path source) throws IOException;
}
