package io.github.mralireza82.wirelens.inspection.application.port.out;

/** A file format captures can be written in. */
public interface CaptureFormat {
    /** Shown in file dialogs, e.g. "JSON Lines". */
    String displayName();

    /** Without the dot, e.g. {@code jsonl}. */
    String fileExtension();
}
