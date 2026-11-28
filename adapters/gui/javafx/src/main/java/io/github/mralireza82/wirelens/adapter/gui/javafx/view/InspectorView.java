package io.github.mralireza82.wirelens.adapter.gui.javafx.view;

import atlantafx.base.controls.Spacer;
import atlantafx.base.theme.Styles;
import io.github.mralireza82.wirelens.adapter.gui.javafx.util.Clipboards;
import io.github.mralireza82.wirelens.adapter.gui.javafx.util.Formats;
import io.github.mralireza82.wirelens.adapter.gui.javafx.viewmodel.TrafficEntry;
import io.github.mralireza82.wirelens.adapter.gui.javafx.viewmodel.TrafficPanelViewModel;
import io.github.mralireza82.wirelens.inspection.application.render.PayloadRenderer;
import io.github.mralireza82.wirelens.inspection.application.render.RenderSettings;
import io.github.mralireza82.wirelens.kernel.bytes.Payload;
import io.github.mralireza82.wirelens.relay.domain.traffic.TrafficEvent;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;

/**
 * Shows the selected entry in full: numbers, decoded text, a classic hex dump and all metadata, in read-only,
 * selectable text areas, with one-click copy of each representation and of the raw bytes.
 */
final class InspectorView extends VBox {
    private final TrafficPanelViewModel model;
    private final PayloadRenderer renderer;
    private final Label title = new Label();
    private final TextArea numbers = area(true);
    private final TextArea text = area(true);
    private final TextArea dump = area(false);
    private final TextArea details = area(false);
    private final Tab numbersTab = new Tab(null, numbers);
    private final Tab textTab = new Tab(null, text);
    private final Tab dumpTab = new Tab("Hex dump", dump);
    private final Tab detailsTab = new Tab("Details", details);
    private final TabPane tabs = new TabPane(numbersTab, textTab, dumpTab, detailsTab);
    private final HBox actions;
    private final Label placeholder = new Label("Select an entry to see all of its bytes, copy them or save them.");

    InspectorView(TrafficPanelViewModel model, PayloadRenderer renderer, Consumer<Payload> saveRaw) {
        this.model = model;
        this.renderer = renderer;
        getStyleClass().add("inspector");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add(Styles.TABS_CLASSIC);
        tabs.setMinHeight(0);
        title.getStyleClass().add("inspector-title");
        title.setMinWidth(0);

        Button copyNumbers = action(Feather.HASH, "Copy numbers", () -> Clipboards.copy(numbers.getText()));
        Button copyText = action(Feather.TYPE, "Copy decoded text", () -> Clipboards.copy(text.getText()));
        Button copyRaw = action(Feather.COPY, "Copy raw bytes as canonical hex (no separators)",
                () -> payload().ifPresent(p -> Clipboards.copy(renderer.rawHex(p))));
        Button save = action(Feather.SAVE, "Save raw bytes to a .bin file",
                () -> payload().ifPresent(saveRaw));
        Button disconnect = action(Feather.SLASH, "Disconnect this connection (both sides)",
                model::disconnectSelected);
        disconnect.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> model.selected().get() == null || model.selected().get().event().connectionId()
                        .filter(model.openConnections()::contains).isEmpty(),
                model.selected(), model.openConnections()));

        ComboBox<Integer> perLine = new ComboBox<>(FXCollections.observableArrayList(0, 8, 16, 24, 32, 64));
        perLine.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : value == 0 ? "wrap" : value + " / line";
            }

            @Override
            public Integer fromString(String string) {
                return null;
            }
        });
        perLine.setTooltip(new Tooltip("Bytes per line in the numbers view"));
        perLine.setValue(model.settings().get().bytesPerLine());
        perLine.valueProperty().addListener((obs, old, now) -> {
            if (now != null) {
                model.settings().set(model.settings().get().withBytesPerLine(now));
            }
        });
        perLine.getStyleClass().add(Styles.SMALL);

        actions = new HBox(4, title, new Spacer(), perLine, copyNumbers, copyText, copyRaw, save, disconnect);
        actions.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);

        placeholder.getStyleClass().add("hint");
        placeholder.setWrapText(true);
        StackPane body = new StackPane(tabs, placeholder);
        VBox.setVgrow(body, Priority.ALWAYS);
        getChildren().addAll(actions, body);

        model.selected().addListener((obs, old, now) -> show());
        model.settings().addListener((obs, old, now) -> show());
        show();
    }

    private java.util.Optional<Payload> payload() {
        TrafficEntry entry = model.selected().get();
        return entry != null && entry.event() instanceof TrafficEvent.DataTransferred data
                ? java.util.Optional.of(data.payload()) : java.util.Optional.empty();
    }

    private void show() {
        TrafficEntry entry = model.selected().get();
        RenderSettings view = model.settings().get();
        numbersTab.setText("Numbers · " + view.radix().shortName());
        textTab.setText("Text · " + view.encoding().displayName());
        boolean hasEntry = entry != null;
        tabs.setVisible(hasEntry);
        placeholder.setVisible(!hasEntry);
        actions.setDisable(!hasEntry);
        if (!hasEntry) {
            title.setText("Inspector");
            return;
        }
        TrafficEvent event = entry.event();
        details.setText(details(entry));
        if (event instanceof TrafficEvent.DataTransferred data) {
            Payload payload = data.payload();
            title.setText("#" + entry.number() + " · " + EntryPresenter.badge(event).text().toLowerCase() + " "
                    + Formats.count(payload.size()) + " bytes");
            numbers.setWrapText(view.bytesPerLine() == 0);
            numbers.setText(renderer.numbers(payload, view));
            text.setText(renderer.text(payload, view.encoding()));
            dump.setText(renderer.hexDump(payload, view.encoding()));
            for (Tab tab : new Tab[] {numbersTab, textTab, dumpTab}) {
                tab.setDisable(false);
            }
        } else {
            title.setText("#" + entry.number() + " · " + EntryPresenter.badge(event).text().toLowerCase());
            for (TextArea area : new TextArea[] {numbers, text, dump}) {
                area.setText("");
            }
            for (Tab tab : new Tab[] {numbersTab, textTab, dumpTab}) {
                tab.setDisable(true);
            }
            tabs.getSelectionModel().select(detailsTab);
        }
    }

    private String details(TrafficEntry entry) {
        TrafficEvent event = entry.event();
        StringBuilder text = new StringBuilder();
        row(text, "Entry", "#" + entry.number());
        row(text, "Time", Formats.dateTime(event.timestamp()));
        row(text, "Side", event.side() + " (" + EntryPresenter.peerName(event.side()) + " leg)");
        row(text, "Event", EntryPresenter.badge(event).text() + (EntryPresenter.isTyped(event) ? " (" + EntryPresenter.originTag(event).toLowerCase(java.util.Locale.ROOT) + " by you)" : ""));
        event.connectionId().ifPresent(id -> row(text, "Connection", id.toString()));
        if (event instanceof TrafficEvent.OnLink onLink) {
            row(text, "Proxy end", onLink.link().local().toString());
            row(text, "Peer", onLink.link().remote() + " (" + EntryPresenter.peerName(event.side()) + ")");
        } else {
            event.remote().ifPresent(remote -> row(text, "Peer", remote.toString()));
        }
        switch (event) {
            case TrafficEvent.DataTransferred data -> {
                row(text, "Direction", data.direction() + (data.direction().name().equals("INBOUND")
                        ? " (received by the proxy)" : " (sent by the proxy)"));
                row(text, "Size", Formats.count(data.payload().size()) + " bytes");
                row(text, "Captured as", entry.capturedWith().summary()
                        + " (the view settings when it arrived; raw bytes are what is stored)");
                row(text, "Raw (hex)", renderer.rawHex(data.payload()));
            }
            case TrafficEvent.ConnectionClosed closed -> {
                row(text, "Reason", closed.reason());
                row(text, "Received", Formats.count(closed.bytesIn()) + " bytes");
                row(text, "Sent", Formats.count(closed.bytesOut()) + " bytes");
                row(text, "Duration", closed.duration().toMillis() + " ms");
            }
            case TrafficEvent.ConnectionFailed failed -> {
                row(text, "Stage", failed.stage().toString());
                row(text, "Message", failed.message());
            }
            default -> row(text, "Message", EntryPresenter.message(event));
        }
        return text.toString();
    }

    private static void row(StringBuilder text, String key, String value) {
        text.append(String.format("%-12s %s%n", key, value));
    }

    private static TextArea area(boolean wrap) {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(wrap);
        area.getStyleClass().add("mono");
        area.setMinHeight(0);
        area.setPrefRowCount(6);
        return area;
    }

    private static Button action(Feather icon, String tooltip, Runnable run) {
        Button button = new Button(null, new FontIcon(icon));
        button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(event -> run.run());
        return button;
    }
}
