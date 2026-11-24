package io.github.mralireza82.wirelens.adapter.javafx.viewmodel;

/** Shows a short, non-blocking message to the user (a toast). */
@FunctionalInterface
public interface Notifier {
    void notify(String message, boolean error);
}
