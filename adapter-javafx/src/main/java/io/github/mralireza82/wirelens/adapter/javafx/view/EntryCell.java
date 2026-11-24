package io.github.mralireza82.wirelens.adapter.javafx.view;

import io.github.mralireza82.wirelens.adapter.javafx.util.Formats;
import io.github.mralireza82.wirelens.adapter.javafx.viewmodel.TrafficEntry;
import io.github.mralireza82.wirelens.application.render.PayloadRenderer;
import io.github.mralireza82.wirelens.application.render.RenderSettings;
import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Renders one traffic entry as a compact card: metadata line, then the numbers and the decoded text, each with
 * a small tag (HEX / TEXT) so the representations are never confused. Previews are capped; the inspector shows
 * everything.
 */
final class EntryCell extends ListCell<TrafficEntry> {
    static final int PREVIEW_NUMBER_BYTES = 48;
    static final int PREVIEW_TEXT_BYTES = 160;

    private final ObservableValue<RenderSettings> settings;
    private final PayloadRenderer renderer;

    private final Label number = label("entry-number");
    private final Label time = label("entry-time", "mono");
    private final Label badge = label("badge");
    private final Label typed = label("badge", "typed");
    private final Label connection = label("entry-connection");
    private final Label peer = label("entry-peer", "mono");
    private final Label size = label("entry-size");
    private final Label numbersTag = label("repr-tag");
    private final Label numbers = label("payload-numbers", "mono");
    private final Label textTag = label("repr-tag");
    private final Label text = label("payload-text", "mono");
    private final Label message = label("entry-message");
    private final HBox numbersRow = new HBox(numbersTag, numbers);
    private final HBox textRow = new HBox(textTag, text);
    private final VBox card;

    EntryCell(ObservableValue<RenderSettings> settings, PayloadRenderer renderer, ContextMenu menu) {
        this.settings = settings;
        this.renderer = renderer;
        typed.setText("TYPED");
        textTag.setText("TEXT");
        peer.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        peer.setMinWidth(0);
        HBox.setHgrow(peer, Priority.ALWAYS);
        peer.setMaxWidth(Double.MAX_VALUE);
        size.setMinWidth(Label.USE_PREF_SIZE);
        badge.setMinWidth(Label.USE_PREF_SIZE);
        time.setMinWidth(Label.USE_PREF_SIZE);
        for (Label payload : new Label[] {numbers, text, message}) {
            payload.setWrapText(true);
            payload.setMinWidth(0);
            payload.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(payload, Priority.ALWAYS);
        }
        numbersRow.getStyleClass().add("entry-row");
        textRow.getStyleClass().add("entry-row");
        HBox meta = new HBox(number, time, badge, typed, connection, peer, size);
        meta.getStyleClass().add("entry-meta");
        card = new VBox(meta, numbersRow, textRow, message);
        card.getStyleClass().add("entry");
        card.setMinWidth(0);
        listViewProperty().addListener((obs, old, list) -> {
            card.prefWidthProperty().unbind();
            if (list != null) {
                card.prefWidthProperty().bind(list.widthProperty().subtract(24));
            }
        });
        setContextMenu(menu);
        setText(null);
    }

    @Override
    protected void updateItem(TrafficEntry entry, boolean empty) {
        super.updateItem(entry, empty);
        if (empty || entry == null) {
            setGraphic(null);
            return;
        }
        TrafficEvent event = entry.event();
        RenderSettings view = settings.getValue();
        EntryPresenter.Badge kind = EntryPresenter.badge(event);
        number.setText(Long.toString(entry.number()));
        time.setText(Formats.time(event.timestamp()));
        badge.setText(kind.text());
        badge.setGraphic(new FontIcon(kind.icon()));
        badge.getStyleClass().setAll("label", "badge", kind.styleClass());
        show(typed, EntryPresenter.isTyped(event));
        connection.setText(event.connectionId().map(Object::toString).orElse(""));
        peer.setText(EntryPresenter.peer(event));

        if (event instanceof TrafficEvent.DataTransferred data) {
            int bytes = data.payload().size();
            size.setText(bytes + " B");
            numbersTag.setText(view.radix().shortName());
            numbers.setText(withMore(entry.previewNumbers(renderer, view, PREVIEW_NUMBER_BYTES), bytes,
                    PREVIEW_NUMBER_BYTES));
            text.setText(withMore(entry.previewText(renderer, view, PREVIEW_TEXT_BYTES), bytes, PREVIEW_TEXT_BYTES));
            show(numbersRow, true);
            show(textRow, true);
            show(message, false);
        } else {
            size.setText("");
            message.setText(EntryPresenter.message(event));
            show(numbersRow, false);
            show(textRow, false);
            show(message, true);
        }
        setGraphic(card);
    }

    private static String withMore(String preview, int total, int shown) {
        return total > shown ? preview + "  … +" + (total - shown) + " B" : preview;
    }

    private static void show(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static Label label(String... styleClasses) {
        Label label = new Label();
        label.getStyleClass().addAll(styleClasses);
        return label;
    }
}
