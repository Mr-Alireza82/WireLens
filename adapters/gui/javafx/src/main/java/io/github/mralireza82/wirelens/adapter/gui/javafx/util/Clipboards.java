package io.github.mralireza82.wirelens.adapter.gui.javafx.util;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/** Puts plain text on the system clipboard. */
public final class Clipboards {
    private Clipboards() {
    }

    public static void copy(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
