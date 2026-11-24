package io.github.mralireza82.wirelens.application.port.out;

import io.github.mralireza82.wirelens.domain.traffic.Payload;

import java.io.IOException;
import java.nio.file.Path;

/** Saves the exact bytes of one payload to a file. */
@FunctionalInterface
public interface PayloadFileWriter {
    void write(Payload payload, Path target) throws IOException;
}
