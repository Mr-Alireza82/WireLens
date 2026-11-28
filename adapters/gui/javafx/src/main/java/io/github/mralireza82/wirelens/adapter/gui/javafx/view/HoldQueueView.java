package io.github.mralireza82.wirelens.adapter.gui.javafx.view;

import atlantafx.base.controls.Spacer;
import atlantafx.base.theme.Styles;
import io.github.mralireza82.wirelens.adapter.gui.javafx.util.Formats;
import io.github.mralireza82.wirelens.adapter.gui.javafx.viewmodel.ComposerMode;
import io.github.mralireza82.wirelens.adapter.gui.javafx.viewmodel.EndpointInput;
import io.github.mralireza82.wirelens.adapter.gui.javafx.viewmodel.TrafficPanelViewModel;
import io.github.mralireza82.wirelens.inspection.application.render.PayloadRenderer;
import io.github.mralireza82.wirelens.inspection.application.render.PayloadSyntaxException;
import io.github.mralireza82.wirelens.inspection.application.render.RenderSettings;
import io.github.mralireza82.wirelens.inspection.application.render.TextEncoding;
import io.github.mralireza82.wirelens.relay.domain.intercept.HeldMessage;
import io.github.mralireza82.wirelens.kernel.net.Endpoint;
import io.github.mralireza82.wirelens.kernel.bytes.Payload;
import io.github.mralireza82.wirelens.relay.domain.traffic.Side;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The hold queue of one panel, visible while Hold is on: messages received on this side that wait for a decision,
 * oldest first, with Forward, Edit &amp; forward, Drop and Send copy for the selected one, and Forward all / Drop all.
 */
final class HoldQueueView extends VBox {
    private static final int PREVIEW_BYTES = 64;

    private final TrafficPanelViewModel model;
    private final PayloadRenderer renderer;
    private final Supplier<Optional<Endpoint>> defaultCopyTarget;

    HoldQueueView(TrafficPanelViewModel model, PayloadRenderer renderer, Supplier<Optional<Endpoint>> copyTarget) {
        this.model = model;
        this.renderer = renderer;
        this.defaultCopyTarget = copyTarget;
        getStyleClass().add("hold-queue");
        visibleProperty().bind(model.holding());
        managedProperty().bind(visibleProperty());

        String from = model.side() == Side.SOURCE ? "client" : "destination";
        String to = model.side() == Side.SOURCE ? "destination" : "client";
        Label title = new Label(null, new FontIcon(Feather.OCTAGON));
        title.getStyleClass().add("hold-title");
        title.textProperty().bind(Bindings.createStringBinding(() -> model.held().isEmpty()
                ? "Holding: messages from the " + from + " will wait here"
                : Formats.count(model.held().size()) + " held · waiting to go to the " + to, model.held()));
        title.setMinWidth(0);
        Button forwardAll = small("Forward all", Feather.CHEVRONS_RIGHT, "Send every held message on, in order");
        forwardAll.setOnAction(event -> model.forwardAllHeld());
        Button dropAll = small("Drop all", Feather.X_OCTAGON, "Discard every held message");
        dropAll.getStyleClass().add(Styles.DANGER);
        dropAll.setOnAction(event -> model.dropAllHeld());
        forwardAll.disableProperty().bind(Bindings.isEmpty(model.held()));
        dropAll.disableProperty().bind(Bindings.isEmpty(model.held()));
        HBox header = new HBox(8, title, new Spacer(), forwardAll, dropAll);
        header.setAlignment(Pos.CENTER_LEFT);

        ListView<HeldMessage> list = new ListView<>(model.held());
        list.getStyleClass().addAll("hold-list", Styles.DENSE);
        list.setPrefHeight(132);
        list.setMinHeight(60);
        Label empty = new Label("Nothing held yet.");
        empty.getStyleClass().add("hint");
        list.setPlaceholder(empty);
        list.setCellFactory(view -> new HeldCell());
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, now) -> {
            if (now != null) {
                model.selectedHeld().set(now);
            }
        });
        model.selectedHeld().addListener((obs, old, now) -> {
            if (now != null && list.getSelectionModel().getSelectedItem() != now) {
                list.getSelectionModel().select(now);
            }
        });

        Button forward = small("Forward", Feather.ARROW_RIGHT, "Send the selected message on unchanged");
        forward.getStyleClass().add(Styles.SUCCESS);
        forward.setOnAction(event -> withSelected(m -> model.forwardHeld(m.id())));
        Button edit = small("Edit & forward…", Feather.EDIT_3, "Change the bytes, then send them on");
        edit.setOnAction(event -> withSelected(this::edit));
        Button drop = small("Drop", Feather.X, "Discard the selected message");
        drop.setOnAction(event -> withSelected(m -> model.dropHeld(m.id())));
        Button copy = small("Send copy to…", Feather.SHARE, "Send these bytes to another address and see the reply;"
                + " the message stays held");
        copy.setOnAction(event -> withSelected(this::sendCopy));
        for (Button button : new Button[] {forward, edit, drop, copy}) {
            button.disableProperty().bind(model.selectedHeld().isNull());
        }
        HBox actions = new HBox(6, forward, edit, drop, new Spacer(), copy);
        actions.setAlignment(Pos.CENTER_LEFT);

        setSpacing(6);
        getChildren().addAll(header, list, actions);
    }

    private void withSelected(java.util.function.Consumer<HeldMessage> action) {
        HeldMessage message = model.selectedHeld().get();
        if (message != null) {
            action.accept(message);
        }
    }

    private static Button small(String text, Feather icon, String tip) {
        Button button = new Button(text, new FontIcon(icon));
        button.getStyleClass().add(Styles.SMALL);
        button.setMinWidth(Button.USE_PREF_SIZE);
        button.setTooltip(new Tooltip(tip));
        return button;
    }

    // ------------------------------------------------------------------ dialogs

    private void edit(HeldMessage message) {
        BytesEditor editor = new BytesEditor(model, renderer, message.payload());
        Dialog<Payload> dialog = dialog("Edit " + message.id() + " before forwarding",
                message.payload().size() + " bytes from the " + peer(message.receivedOn()) + " of "
                        + message.connection() + ". Edit the bytes or the text, then forward.",
                editor, "Forward edited");
        dialog.setResultConverter(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE
                ? editor.payload().orElse(null) : null);
        dialog.showAndWait().ifPresent(payload -> model.forwardEdited(message.id(), payload));
    }

    private void sendCopy(HeldMessage message) {
        EndpointInput target = defaultCopyTarget.get()
                .map(endpoint -> new EndpointInput(endpoint.address().toString(),
                        Integer.toString(endpoint.port().value())))
                .orElseGet(() -> new EndpointInput("", ""));
        TextField address = new TextField();
        address.textProperty().bindBidirectional(target.address());
        address.setPromptText("IP address");
        address.getStyleClass().add("mono");
        HBox.setHgrow(address, Priority.ALWAYS);
        TextField port = new TextField();
        port.textProperty().bindBidirectional(target.port());
        port.setPromptText("port");
        port.setPrefColumnCount(6);
        port.getStyleClass().add("mono");
        Label error = new Label();
        error.getStyleClass().add("field-error");
        error.textProperty().bind(Bindings.createStringBinding(() -> !target.addressError().get().isEmpty()
                ? target.addressError().get() : target.portError().get(), target.addressError(), target.portError()));
        BytesEditor editor = new BytesEditor(model, renderer, message.payload());
        HBox endpointRow = new HBox(6, new Label("To"), address, new Label(":"), port);
        endpointRow.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(8, endpointRow, error, editor);
        Dialog<Payload> dialog = dialog("Send a copy of " + message.id(),
                "Opens a new connection, sends the bytes and shows the reply in this panel."
                        + " The held message itself keeps waiting.", content, "Send copy");
        Button ok = (Button) dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().getFirst());
        ok.disableProperty().bind(target.valid().not());
        dialog.setResultConverter(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE
                ? editor.payload().orElse(null) : null);
        dialog.showAndWait().ifPresent(payload -> target.endpoint()
                .ifPresent(endpoint -> model.sendCopy(message.id(), payload, endpoint)));
    }

    private Dialog<Payload> dialog(String title, String header, javafx.scene.Node content, String okText) {
        Dialog<Payload> dialog = new Dialog<>();
        Window owner = getScene() == null ? null : getScene().getWindow();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        ButtonType ok = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(640);
        dialog.setResizable(true);
        return dialog;
    }

    private static String peer(Side side) {
        return side == Side.SOURCE ? "client" : "destination";
    }

    /** One held message: id, connection, size and a hex preview. */
    private final class HeldCell extends ListCell<HeldMessage> {
        @Override
        protected void updateItem(HeldMessage item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label head = new Label(item.id() + "  " + item.connection() + "  from " + item.from().remote() + "  ·  "
                    + item.payload().size() + " B  ·  " + Formats.time(item.heldAt()));
            head.getStyleClass().add("hold-head");
            RenderSettings view = model.settings().get().withBytesPerLine(0);
            String preview = renderer.numbers(item.payload(), view, PREVIEW_BYTES)
                    + (item.payload().size() > PREVIEW_BYTES ? "  …" : "");
            Label bytes = new Label(preview);
            bytes.getStyleClass().add("mono");
            bytes.setMinWidth(0);
            setText(null);
            setGraphic(new VBox(1, head, bytes));
        }
    }

    /** Bytes or text editing of a payload, parsed with the panel's base and encoding, validated as you type. */
    static final class BytesEditor extends VBox {
        private final TrafficPanelViewModel model;
        private final TextArea area = new TextArea();
        private final Label error = new Label();
        private ComposerMode mode = ComposerMode.NUMBERS;

        BytesEditor(TrafficPanelViewModel model, PayloadRenderer renderer, Payload initial) {
            this.model = model;
            RenderSettings view = model.settings().get();
            ToggleGroup modes = new ToggleGroup();
            ToggleButton bytes = new ToggleButton("Bytes · " + view.radix().shortName());
            ToggleButton text = new ToggleButton("Text · " + view.encoding().displayName());
            bytes.getStyleClass().addAll(Styles.LEFT_PILL, Styles.SMALL);
            text.getStyleClass().addAll(Styles.RIGHT_PILL, Styles.SMALL);
            bytes.setToggleGroup(modes);
            text.setToggleGroup(modes);
            bytes.setSelected(true);
            area.setWrapText(true);
            area.setPrefRowCount(10);
            area.getStyleClass().add("mono");
            area.setText(renderer.numbers(initial, view.withBytesPerLine(16)));
            modes.selectedToggleProperty().addListener((obs, old, now) -> {
                if (now == null) {
                    old.setSelected(true);
                    return;
                }
                Optional<Payload> current = payload();
                if (current.isEmpty()) {
                    old.setSelected(true); // keep the user's text until it parses
                    return;
                }
                mode = now == text ? ComposerMode.TEXT : ComposerMode.NUMBERS;
                area.setText(mode == ComposerMode.TEXT ? escaped(current.get())
                        : renderer.numbers(current.get(), view.withBytesPerLine(16)));
            });
            error.getStyleClass().add("field-error");
            error.setWrapText(true);
            area.textProperty().addListener((obs, old, now) -> validate());
            Label size = new Label();
            size.getStyleClass().add("hint");
            area.textProperty().addListener((obs, old, now) -> size.setText(payload()
                    .map(p -> p.size() + " bytes").orElse("")));
            size.setText(initial.size() + " bytes");
            HBox top = new HBox(8, new HBox(bytes, text), new Spacer(), size);
            top.setAlignment(Pos.CENTER_LEFT);
            setSpacing(6);
            VBox.setVgrow(area, Priority.ALWAYS);
            getChildren().addAll(top, area, error);
        }

        private void validate() {
            try {
                model.parseInput(area.getText(), mode);
                error.setText("");
            } catch (PayloadSyntaxException e) {
                error.setText(e.getMessage());
            }
        }

        Optional<Payload> payload() {
            try {
                return Optional.of(Payload.copyOf(model.parseInput(area.getText(), mode)));
            } catch (PayloadSyntaxException e) {
                return Optional.empty();
            }
        }

        /**
         * Text that parses back to exactly these bytes: printable ASCII as is and escapes for the rest. For encodings
         * that are not ASCII-compatible (EBCDIC, UTF-16) every byte is escaped, since a literal character would be
         * re-encoded differently.
         */
        private String escaped(Payload payload) {
            TextEncoding encoding = model.settings().get().encoding();
            boolean literal = encoding == TextEncoding.ASCII || encoding == TextEncoding.ISO_8859_1
                    || encoding == TextEncoding.UTF_8;
            StringBuilder out = new StringBuilder();
            byte[] bytes = payload.toByteArray();
            for (byte b : bytes) {
                int value = b & 0xFF;
                if (!literal) {
                    out.append(String.format("\\x%02X", value));
                    continue;
                }
                switch (value) {
                    case '\r' -> out.append("\\r");
                    case '\n' -> out.append("\\n");
                    case '\t' -> out.append("\\t");
                    case '\\' -> out.append("\\\\");
                    default -> out.append(value >= 0x20 && value < 0x7F ? String.valueOf((char) value)
                            : String.format("\\x%02X", value));
                }
            }
            return out.toString();
        }
    }
}
