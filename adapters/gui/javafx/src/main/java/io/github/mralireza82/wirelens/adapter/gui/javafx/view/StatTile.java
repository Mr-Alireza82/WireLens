package io.github.mralireza82.wirelens.adapter.gui.javafx.view;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** A header tile: small caption, big value, muted detail line. */
final class StatTile extends VBox {
    final Label caption = new Label();
    final Label value = new Label();
    final Label detail = new Label();

    StatTile(String captionText, Node icon) {
        getStyleClass().add("stat-tile");
        caption.setText(captionText);
        caption.setGraphic(icon);
        caption.getStyleClass().add("stat-label");
        value.getStyleClass().add("stat-value");
        detail.getStyleClass().add("stat-detail");
        getChildren().addAll(caption, value, detail);
        setMinWidth(110);
        setPrefWidth(160);
        setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(this, Priority.ALWAYS);
    }
}
