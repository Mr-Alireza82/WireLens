package io.github.mralireza82.wirelens.adapter.javafx.view;

import io.github.mralireza82.wirelens.adapter.javafx.viewmodel.Notifier;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Short messages that appear at the bottom of the window and fade away; never steal focus. */
public final class Toasts implements Notifier {
    private final StackPane host;
    private final Label toast = new Label();
    private SequentialTransition animation;

    public Toasts(StackPane host) {
        this.host = host;
        toast.getStyleClass().add("toast");
        toast.setWrapText(true);
        toast.setMaxWidth(560);
        toast.setMouseTransparent(true);
        toast.setOpacity(0);
        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new Insets(0, 0, 28, 0));
        host.getChildren().add(toast);
    }

    @Override
    public void notify(String message, boolean error) {
        toast.setText(message);
        toast.setGraphic(new FontIcon(error ? Feather.ALERT_TRIANGLE : Feather.CHECK_CIRCLE));
        toast.getStyleClass().remove("error");
        if (error) {
            toast.getStyleClass().add("error");
        }
        toast.toFront();
        if (animation != null) {
            animation.stop();
        }
        FadeTransition in = new FadeTransition(Duration.millis(150), toast);
        in.setToValue(1);
        FadeTransition out = new FadeTransition(Duration.millis(400), toast);
        out.setToValue(0);
        animation = new SequentialTransition(in, new PauseTransition(Duration.seconds(error ? 6 : 3.5)), out);
        animation.play();
    }
}
