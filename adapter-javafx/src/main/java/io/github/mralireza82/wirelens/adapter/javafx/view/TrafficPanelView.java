package io.github.mralireza82.wirelens.adapter.javafx.view;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.Spacer;
import atlantafx.base.theme.Styles;
import io.github.mralireza82.wirelens.adapter.javafx.util.Clipboards;
import io.github.mralireza82.wirelens.adapter.javafx.util.Formats;
import io.github.mralireza82.wirelens.adapter.javafx.viewmodel.ComposerMode;
import io.github.mralireza82.wirelens.adapter.javafx.viewmodel.EndpointInput;
import io.github.mralireza82.wirelens.adapter.javafx.viewmodel.MainViewModel;
import io.github.mralireza82.wirelens.adapter.javafx.viewmodel.TrafficEntry;
import io.github.mralireza82.wirelens.adapter.javafx.viewmodel.TrafficPanelViewModel;
import io.github.mralireza82.wirelens.application.port.out.CaptureExporter;
import io.github.mralireza82.wirelens.application.render.PayloadRenderer;
import io.github.mralireza82.wirelens.application.render.Radix;
import io.github.mralireza82.wirelens.application.render.RenderSettings;
import io.github.mralireza82.wirelens.application.render.TextEncoding;
import io.github.mralireza82.wirelens.domain.traffic.EventKind;
import io.github.mralireza82.wirelens.domain.traffic.Payload;
import io.github.mralireza82.wirelens.domain.traffic.Side;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * One traffic panel: its endpoint (listen address for the source, destination for the other), independent view
 * selectors, search and capture controls, the entry list, the inspector and the send box.
 */
public final class TrafficPanelView extends VBox {
    private static final Map<String, String> SEPARATOR_PRESETS = new LinkedHashMap<>();

    static {
        SEPARATOR_PRESETS.put("space", " ");
        SEPARATOR_PRESETS.put("none", "");
        SEPARATOR_PRESETS.put("-", "-");
        SEPARATOR_PRESETS.put(":", ":");
        SEPARATOR_PRESETS.put(", ", ", ");
        SEPARATOR_PRESETS.put(" | ", " | ");
    }

    private final TrafficPanelViewModel model;
    private final MainViewModel main;
    private final PayloadRenderer renderer = new PayloadRenderer();
    private final ListView<TrafficEntry> list = new ListView<>();

    public TrafficPanelView(TrafficPanelViewModel model, MainViewModel main) {
        this.model = model;
        this.main = main;
        boolean source = model.side() == Side.SOURCE;
        getStyleClass().addAll("panel", source ? "source" : "destination");
        setMinWidth(320);

        SplitPane body = new SplitPane(listPane(), bottomPane());
        body.setOrientation(Orientation.VERTICAL);
        body.setDividerPositions(0.62);
        VBox.setVgrow(body, Priority.ALWAYS);

        getChildren().addAll(titleBar(source), endpointForm(source), viewBar(), filterBar(), body, statusLine());
        model.settings().addListener((obs, old, now) -> {
            list.refresh();
            main.saveSettings();
        });
    }

    // ------------------------------------------------------------------ sections

    private Node titleBar(boolean source) {
        Region accent = new Region();
        accent.getStyleClass().add("panel-accent");
        FontIcon icon = new FontIcon(source ? Feather.LOG_IN : Feather.SERVER);
        icon.getStyleClass().add("panel-icon");
        Label title = new Label(source ? "Source" : "Destination");
        title.getStyleClass().add("panel-title");
        Label subtitle = new Label(source ? "Incoming traffic from clients" : "Outgoing traffic to the destination");
        subtitle.getStyleClass().add("panel-subtitle");
        subtitle.setMinWidth(0);
        Label counter = new Label();
        counter.getStyleClass().add("counter");
        counter.textProperty().bind(Bindings.createStringBinding(
                () -> Formats.count(model.openConnections().size()) + " open", model.openConnections()));
        HBox bar = new HBox(8, accent, icon, title, subtitle, new Spacer(), counter);
        bar.setAlignment(Pos.CENTER_LEFT);
        accent.setPrefHeight(22);
        return bar;
    }

    private Node endpointForm(boolean source) {
        EndpointInput input = model.endpoint();
        Label caption = new Label(source ? "Listen on" : "Forward to");
        caption.getStyleClass().add("field-label");
        caption.setMinWidth(Label.USE_PREF_SIZE);

        CustomTextField address = new CustomTextField();
        address.setLeft(new FontIcon(Feather.GLOBE));
        address.setPromptText(source ? "IP address, e.g. 127.0.0.1, 0.0.0.0 or ::" : "IPv4 or IPv6, e.g. 10.0.0.5");
        address.textProperty().bindBidirectional(input.address());
        address.getStyleClass().add("mono");
        address.setPrefColumnCount(18);
        address.setMinWidth(120);
        HBox.setHgrow(address, Priority.ALWAYS);

        MenuButton presets = new MenuButton();
        presets.setGraphic(new FontIcon(Feather.CHEVRON_DOWN));
        presets.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, "no-arrow");
        presets.setTooltip(new Tooltip("Common addresses"));
        Map<String, String> choices = new LinkedHashMap<>();
        choices.put("127.0.0.1", "this computer only (IPv4)");
        choices.put("::1", "this computer only (IPv6)");
        if (source) {
            choices.put("0.0.0.0", "all IPv4 interfaces");
            choices.put("::", "all interfaces (IPv6 and usually IPv4)");
        }
        choices.forEach((value, meaning) -> {
            MenuItem item = new MenuItem(value + "   " + meaning);
            item.setOnAction(event -> input.address().set(value));
            presets.getItems().add(item);
        });

        Label colon = new Label(":");
        TextField port = new TextField();
        port.setPromptText("port");
        port.setPrefColumnCount(5);
        port.setMinWidth(70);
        port.getStyleClass().add("mono");
        port.textProperty().bindBidirectional(input.port());

        markInvalid(address, input.addressError().isNotEmpty().get());
        input.addressError().addListener((obs, old, now) -> markInvalid(address, !now.isEmpty()));
        markInvalid(port, input.portError().isNotEmpty().get());
        input.portError().addListener((obs, old, now) -> markInvalid(port, !now.isEmpty()));

        HBox row = new HBox(6, caption, address, presets, colon, port);
        row.setAlignment(Pos.CENTER_LEFT);

        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.setMinHeight(Label.USE_PREF_SIZE);
        if (source) {
            // The listening socket cannot move while it is open.
            address.disableProperty().bind(Bindings.createBooleanBinding(
                    () -> main.status().get().state().isActive(), main.status()));
            port.disableProperty().bind(address.disableProperty());
            presets.disableProperty().bind(address.disableProperty());
            feedback.textProperty().bind(Bindings.createStringBinding(() -> firstError(input,
                    main.status().get().state().isActive() ? "Stop the relay to change where it listens."
                            : "Clients will connect to this address. Use 0.0.0.0 to accept other machines."),
                    input.addressError(), input.portError(), main.status()));
        } else {
            Button apply = new Button("Apply", new FontIcon(Feather.CHECK));
            apply.getStyleClass().add(Styles.SMALL);
            apply.setMinWidth(Button.USE_PREF_SIZE);
            apply.disableProperty().bind(main.canApplyDestination().not());
            apply.setTooltip(new Tooltip("Send new connections to this destination"));
            apply.setOnAction(event -> main.applyDestination());
            address.setOnAction(event -> {
                if (!apply.isDisabled()) {
                    main.applyDestination();
                }
            });
            row.getChildren().add(apply);
            feedback.textProperty().bind(Bindings.createStringBinding(() -> firstError(input,
                    main.status().get().state().isActive()
                            ? "Changes apply to new connections; open ones keep their destination."
                            : "Every client is forwarded here. IPv4 and IPv6 are both supported."),
                    input.addressError(), input.portError(), main.status()));
        }
        feedback.getStyleClass().add("hint");
        input.valid().addListener((obs, old, valid) -> {
            feedback.getStyleClass().removeAll("hint", "field-error");
            feedback.getStyleClass().add(valid ? "hint" : "field-error");
        });
        if (!input.valid().get()) {
            feedback.getStyleClass().setAll("label", "field-error");
        }
        return new VBox(4, row, feedback);
    }

    private static String firstError(EndpointInput input, String hint) {
        if (!input.addressError().get().isEmpty()) {
            return input.addressError().get();
        }
        if (!input.portError().get().isEmpty()) {
            return input.portError().get();
        }
        return hint;
    }

    private static void markInvalid(Node field, boolean invalid) {
        field.pseudoClassStateChanged(Styles.STATE_DANGER, invalid);
    }

    private Node viewBar() {
        ComboBox<TextEncoding> encoding = new ComboBox<>(FXCollections.observableArrayList(TextEncoding.available()));
        encoding.setValue(model.settings().get().encoding());
        encoding.setTooltip(new Tooltip("How bytes are decoded into text"));
        encoding.valueProperty().addListener((obs, old, now) -> {
            if (now != null) {
                model.settings().set(model.settings().get().withEncoding(now));
            }
        });

        ToggleGroup bases = new ToggleGroup();
        HBox baseButtons = new HBox();
        Radix[] radixes = Radix.values();
        for (int i = 0; i < radixes.length; i++) {
            Radix radix = radixes[i];
            ToggleButton button = new ToggleButton(radix.shortName());
            button.setUserData(radix);
            button.setToggleGroup(bases);
            button.setTooltip(new Tooltip("Show bytes in base " + radix.radix()));
            button.getStyleClass().add(i == 0 ? Styles.LEFT_PILL : i == radixes.length - 1 ? Styles.RIGHT_PILL
                    : Styles.CENTER_PILL);
            button.setSelected(radix == model.settings().get().radix());
            button.setMinWidth(Region.USE_PREF_SIZE);
            baseButtons.getChildren().add(button);
        }
        bases.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null) {
                old.setSelected(true); // a base is always selected
                return;
            }
            model.settings().set(model.settings().get().withRadix((Radix) now.getUserData()));
        });

        ComboBox<String> separator = new ComboBox<>(FXCollections.observableArrayList(SEPARATOR_PRESETS.keySet()));
        separator.setEditable(true);
        separator.setPrefWidth(96);
        separator.setTooltip(new Tooltip("Text placed between bytes (display only). Pick one or type your own."));
        separator.setValue(separatorName(model.settings().get().separator()));
        separator.valueProperty().addListener((obs, old, now) -> {
            String value = now == null ? "" : SEPARATOR_PRESETS.getOrDefault(now, now);
            if (value.length() > RenderSettings.MAX_SEPARATOR_LENGTH) {
                separator.setValue(old);
                return;
            }
            model.settings().set(model.settings().get().withSeparator(value));
        });

        HBox bar = new HBox(8, fieldLabel("Encoding"), encoding, fieldLabel("Base"), baseButtons,
                fieldLabel("Separator"), separator);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private static String separatorName(String value) {
        return SEPARATOR_PRESETS.entrySet().stream().filter(entry -> entry.getValue().equals(value))
                .map(Map.Entry::getKey).findFirst().orElse(value);
    }

    private Node filterBar() {
        CustomTextField search = new CustomTextField();
        search.setPromptText("Search text, hex bytes, #connection or address");
        search.setLeft(new FontIcon(Feather.SEARCH));
        Button clearSearch = new Button(null, new FontIcon(Feather.X));
        clearSearch.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        clearSearch.setOnAction(event -> search.clear());
        clearSearch.visibleProperty().bind(search.textProperty().isNotEmpty());
        search.setRight(clearSearch);
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setMinWidth(140);
        PauseTransition debounce = new PauseTransition(Duration.millis(220));
        debounce.setOnFinished(event -> model.query().set(search.getText()));
        search.textProperty().addListener((obs, old, now) -> debounce.playFromStart());
        model.query().addListener((obs, old, now) -> {
            if (!now.equals(search.getText())) {
                search.setText(now);
            }
        });

        MenuButton kinds = new MenuButton(null, new FontIcon(Feather.FILTER));
        kinds.setTooltip(new Tooltip("Choose which kinds of events to show"));
        kinds.getStyleClass().addAll(Styles.BUTTON_ICON, "no-arrow");
        Map<EventKind, String> names = new LinkedHashMap<>();
        names.put(EventKind.DATA, "Data received / sent");
        names.put(EventKind.OPENED, "Connections opened");
        names.put(EventKind.STREAM_ENDED, "End of stream (half-close)");
        names.put(EventKind.CLOSED, "Connections closed");
        names.put(EventKind.FAILED, "Errors");
        names.put(EventKind.NOTICE, "Info messages");
        names.forEach((kind, name) -> {
            CheckMenuItem item = new CheckMenuItem(name);
            item.setSelected(model.kinds().get().contains(kind));
            item.selectedProperty().addListener((obs, old, selected) -> {
                Set<EventKind> now = EnumSet.noneOf(EventKind.class);
                now.addAll(model.kinds().get());
                if (selected) {
                    now.add(kind);
                } else {
                    now.remove(kind);
                }
                model.kinds().set(now);
            });
            kinds.getItems().add(item);
        });

        ToggleButton pause = new ToggleButton();
        pause.getStyleClass().add(Styles.BUTTON_ICON);
        pause.selectedProperty().bindBidirectional(model.paused());
        pause.graphicProperty().bind(Bindings.createObjectBinding(
                () -> new FontIcon(model.paused().get() ? Feather.PLAY : Feather.PAUSE), model.paused()));
        pause.setTooltip(new Tooltip("Pause / resume capturing in this panel (traffic keeps flowing)"));

        ToggleButton follow = new ToggleButton(null, new FontIcon(Feather.CHEVRONS_DOWN));
        follow.getStyleClass().add(Styles.BUTTON_ICON);
        follow.selectedProperty().bindBidirectional(model.followLatest());
        follow.setTooltip(new Tooltip("Follow the newest entries"));

        Button clear = new Button(null, new FontIcon(Feather.TRASH_2));
        clear.getStyleClass().add(Styles.BUTTON_ICON);
        clear.setTooltip(new Tooltip("Clear this panel"));
        clear.setOnAction(event -> model.clear());

        MenuButton export = new MenuButton(null, new FontIcon(Feather.DOWNLOAD));
        export.getStyleClass().addAll(Styles.BUTTON_ICON, "no-arrow");
        export.setTooltip(new Tooltip("Export the entries shown in this panel"));
        for (CaptureExporter exporter : main.exporters()) {
            MenuItem item = new MenuItem(exporter.displayName() + " (." + exporter.fileExtension() + ")");
            item.setOnAction(event -> exportPanel(exporter));
            export.getItems().add(item);
        }

        HBox bar = new HBox(6, search, kinds, pause, follow, clear, export);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private Node listPane() {
        list.setItems(model.visibleEntries());
        list.getStyleClass().addAll("traffic-list", Styles.STRIPED);
        list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        list.setMinHeight(80);
        Label placeholder = new Label(model.side() == Side.SOURCE
                ? "No traffic yet. Start the relay and point a client at the listening address."
                : "No traffic yet. Connections to the destination will appear here.");
        placeholder.getStyleClass().add("hint");
        placeholder.setWrapText(true);
        list.setPlaceholder(placeholder);
        ContextMenu menu = contextMenu();
        list.setCellFactory(view -> new EntryCell(model.settings(), renderer, menu));
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, now) -> model.selected().set(now));
        list.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> model.followLatest().set(false));

        KeyCombination copy = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
        list.setOnKeyPressed(event -> {
            if (copy.match(event)) {
                copySelected();
                event.consume();
            }
        });
        model.visibleEntries().addListener((ListChangeListener<TrafficEntry>) change -> {
            if (model.followLatest().get() && !model.visibleEntries().isEmpty()) {
                list.scrollTo(model.visibleEntries().size() - 1);
            }
        });
        model.followLatest().addListener((obs, old, now) -> {
            if (now && !model.visibleEntries().isEmpty()) {
                list.scrollTo(model.visibleEntries().size() - 1);
            }
        });
        return list;
    }

    private ContextMenu contextMenu() {
        MenuItem copyEntry = new MenuItem("Copy entry", new FontIcon(Feather.CLIPBOARD));
        copyEntry.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN));
        copyEntry.setOnAction(event -> copySelected());
        MenuItem copyNumbers = new MenuItem("Copy numbers", new FontIcon(Feather.HASH));
        copyNumbers.setOnAction(event -> withPayload(p -> Clipboards.copy(renderer.numbers(p,
                model.settings().get().withBytesPerLine(0)))));
        MenuItem copyText = new MenuItem("Copy decoded text", new FontIcon(Feather.TYPE));
        copyText.setOnAction(event -> withPayload(p -> Clipboards.copy(renderer.text(p,
                model.settings().get().encoding()))));
        MenuItem copyRaw = new MenuItem("Copy raw bytes (hex)", new FontIcon(Feather.COPY));
        copyRaw.setOnAction(event -> withPayload(p -> Clipboards.copy(renderer.rawHex(p))));
        MenuItem saveRaw = new MenuItem("Save raw bytes…", new FontIcon(Feather.SAVE));
        saveRaw.setOnAction(event -> withPayload(this::saveRaw));
        MenuItem onlyConnection = new MenuItem("Show only this connection", new FontIcon(Feather.FILTER));
        onlyConnection.setOnAction(event -> selectedEntry().flatMap(e -> e.event().connectionId())
                .ifPresent(model::focusConnection));
        MenuItem disconnect = new MenuItem("Disconnect", new FontIcon(Feather.SLASH));
        disconnect.setOnAction(event -> model.disconnectSelected());
        ContextMenu menu = new ContextMenu(copyEntry, copyNumbers, copyText, copyRaw, saveRaw,
                new SeparatorMenuItem(), onlyConnection, disconnect);
        menu.setOnShowing(event -> {
            boolean data = selectedEntry().map(e -> e.event() instanceof
                    io.github.mralireza82.wirelens.domain.traffic.TrafficEvent.DataTransferred).orElse(false);
            copyNumbers.setDisable(!data);
            copyText.setDisable(!data);
            copyRaw.setDisable(!data);
            saveRaw.setDisable(!data);
            boolean open = selectedEntry().flatMap(e -> e.event().connectionId())
                    .filter(model.openConnections()::contains).isPresent();
            disconnect.setDisable(!open);
            onlyConnection.setDisable(selectedEntry().flatMap(e -> e.event().connectionId()).isEmpty());
        });
        return menu;
    }

    private java.util.Optional<TrafficEntry> selectedEntry() {
        return java.util.Optional.ofNullable(list.getSelectionModel().getSelectedItem());
    }

    private void withPayload(java.util.function.Consumer<Payload> action) {
        selectedEntry().filter(e -> e.event() instanceof
                        io.github.mralireza82.wirelens.domain.traffic.TrafficEvent.DataTransferred)
                .map(e -> ((io.github.mralireza82.wirelens.domain.traffic.TrafficEvent.DataTransferred) e.event())
                        .payload())
                .ifPresent(action);
    }

    private void copySelected() {
        var selected = list.getSelectionModel().getSelectedItems();
        if (!selected.isEmpty()) {
            Clipboards.copy(EntryPresenter.copyText(java.util.List.copyOf(selected), renderer,
                    model.settings().get()));
        }
    }

    private Node bottomPane() {
        InspectorView inspector = new InspectorView(model, renderer, this::saveRaw);
        VBox.setVgrow(inspector, Priority.ALWAYS);
        VBox bottom = new VBox(6, inspector, composer());
        bottom.setMinHeight(0);
        bottom.setPadding(new javafx.geometry.Insets(6, 0, 0, 0));
        return bottom;
    }

    private Node composer() {
        String peer = EntryPresenter.peerName(model.side());
        Label target = new Label();
        target.getStyleClass().addAll("hint", "composer-target");
        target.setGraphic(new FontIcon(Feather.SEND));
        target.textProperty().bind(Bindings.createStringBinding(() -> model.composerTarget().get()
                        .map(id -> "Send to the " + peer + " of " + id)
                        .orElse("Select an entry of an open connection to send bytes to its " + peer),
                model.composerTarget()));

        TextField input = new TextField();
        input.getStyleClass().add("mono");
        input.textProperty().bindBidirectional(model.composerInput());
        input.promptTextProperty().bind(Bindings.createStringBinding(() ->
                        model.composerMode().get() == ComposerMode.NUMBERS
                                ? "Bytes in " + model.settings().get().radix().shortName() + ", e.g. "
                                + example(model.settings().get().radix())
                                : "Text, with escapes \\r \\n \\t \\xHH",
                model.composerMode(), model.settings()));
        HBox.setHgrow(input, Priority.ALWAYS);
        input.setMinWidth(100);
        input.setOnAction(event -> model.send());
        model.composerError().addListener((obs, old, now) -> markInvalid(input, !now.isEmpty()));

        ToggleGroup modes = new ToggleGroup();
        ToggleButton bytes = new ToggleButton("Bytes");
        bytes.getStyleClass().addAll(Styles.LEFT_PILL, Styles.SMALL);
        ToggleButton text = new ToggleButton("Text");
        text.getStyleClass().addAll(Styles.RIGHT_PILL, Styles.SMALL);
        bytes.setToggleGroup(modes);
        text.setToggleGroup(modes);
        (model.composerMode().get() == ComposerMode.TEXT ? text : bytes).setSelected(true);
        modes.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null) {
                old.setSelected(true);
                return;
            }
            model.composerMode().set(now == text ? ComposerMode.TEXT : ComposerMode.NUMBERS);
        });
        model.composerMode().addListener((obs, old, now) -> (now == ComposerMode.TEXT ? text : bytes).setSelected(true));
        HBox modeBox = new HBox(bytes, text);
        modeBox.setMinWidth(Region.USE_PREF_SIZE);

        Button send = new Button("Send", new FontIcon(Feather.SEND));
        send.getStyleClass().add(Styles.SMALL);
        send.setMinWidth(Button.USE_PREF_SIZE);
        send.disableProperty().bind(model.canSend().not());
        send.setOnAction(event -> model.send());

        Label error = new Label();
        error.getStyleClass().add("field-error");
        error.textProperty().bind(model.composerError());
        error.visibleProperty().bind(model.composerError().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());

        HBox row = new HBox(6, modeBox, input, send);
        row.getStyleClass().add("composer");
        row.disableProperty().bind(Bindings.createBooleanBinding(() -> model.composerTarget().get().isEmpty(),
                model.composerTarget()));
        return new VBox(4, target, row, error);
    }

    private static String example(Radix radix) {
        return switch (radix) {
            case HEXADECIMAL -> "02 00 8E 03";
            case DECIMAL -> "2 0 142 3";
            case OCTAL -> "002 000 216 003";
            case BINARY -> "00000010 10001110";
        };
    }

    private Node statusLine() {
        Label status = new Label();
        status.getStyleClass().add("status-line");
        status.textProperty().bind(Bindings.createStringBinding(() -> {
            int total = model.entries().size();
            int shown = model.visibleEntries().size();
            String text = Formats.count(total) + " entries"
                    + (shown != total ? " · " + Formats.count(shown) + " match the filter" : "");
            if (model.paused().get()) {
                text += " · capture paused, " + Formats.count(model.skippedWhilePaused().get()) + " skipped";
            }
            return text;
        }, model.entries(), model.visibleEntries(), model.paused(), model.skippedWhilePaused()));
        model.paused().addListener((obs, old, paused) -> {
            status.getStyleClass().remove("paused");
            if (paused) {
                status.getStyleClass().add("paused");
            }
        });
        Label tip = new Label("Ctrl+C copies selected entries · right-click for more");
        tip.getStyleClass().add("hint");
        tip.setMinWidth(0);
        HBox line = new HBox(8, status, new Spacer(), tip);
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        label.setMinWidth(Label.USE_PREF_SIZE);
        return label;
    }

    // ------------------------------------------------------------------ files

    private void exportPanel(CaptureExporter exporter) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export " + (model.side() == Side.SOURCE ? "source" : "destination") + " traffic");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(exporter.displayName(),
                "*." + exporter.fileExtension()));
        chooser.setInitialFileName("wirelens-" + model.side().name().toLowerCase() + "." + exporter.fileExtension());
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            main.exportPanel(model, exporter, file.toPath());
        }
    }

    private void saveRaw(Payload payload) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save raw bytes");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Binary file", "*.bin"));
        chooser.setInitialFileName("payload.bin");
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            main.saveRawPayload(payload, file.toPath());
        }
    }

    /** For keyboard shortcuts. */
    public void focusList() {
        list.requestFocus();
    }
}
