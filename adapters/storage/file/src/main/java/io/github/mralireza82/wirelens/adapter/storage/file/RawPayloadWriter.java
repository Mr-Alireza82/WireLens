package io.github.mralireza82.wirelens.adapter.storage.file;

import io.github.mralireza82.wirelens.inspection.application.port.out.PayloadFileWriter;

import io.github.mralireza82.wirelens.kernel.bytes.Payload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saves the exact bytes of one payload, e.g. to feed a parser or a hex editor. */
public final class RawPayloadWriter implements io.github.mralireza82.wirelens.inspection.application.port.out.PayloadFileWriter {
    @Override
    public void write(Payload payload, Path target) throws IOException {
        Files.write(target, payload.toByteArray());
    }
}
