package io.github.mralireza82.wirelens.adapter.gui.javafx.view;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.Popover;
import atlantafx.base.controls.Spacer;
import atlantafx.base.theme.Styles;
import io.github.mralireza82.wirelens.adapter.gui.javafx.util.Formats;
import io.github.mralireza82.wirelens.adapter.gui.javafx.viewmodel.ConnectionSummary;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Searchable list of connections, shown in a popover: a search box first, then one row per connection with its
 * route {@code client → proxy → destination}. Used read-only by the header and as the target chooser of each panel.
 *
 * <p>Only open connections are listed. As the one exception, a chosen target that has since closed stays at the top,
 * greyed out, until another connection is chosen, so the user can see why Send is disabled.
 */
final class ConnectionPicker {
    private static final PseudoClass CLOSED = PseudoClass.getPseudoClass("closed");

    private ConnectionPicker() {
    }

    /**
     * A wide button showing the target connection; clicking it opens the searchable list to choose another.
     *
     * @param peer "client" or "destination": whom Send talks to, used in prompts
     */
    static Button targetButton(ObservableList<ConnectionSummary> active, ObjectProperty<ConnectionSummary> target,
                               String peer, Clock clock) {
        Button button = new Button();
        button.getStyleClass().addAll("connection-picker", "mono");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinWidth(120);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        Runnable show = () -> {
            ConnectionSummary current = target.get();
            button.pseudoClassStateChanged(CLOSED, current != null && !current.open());
            if (current == null) {
                button.setText(active.isEmpty() ? "No open connection yet" : "Choose a connection…");
                button.setGraphic(new FontIcon(Feather.LINK));
                button.setTooltip(new Tooltip("Choose the connection whose " + peer + " receives what you send"));
                return;
            }
            String route = current.client() + "  →  " + current.destination().map(Object::toString).orElse("…");
            button.setText(current.id() + "   " + route + (current.open() ? "" : "   · closed"));
            button.setGraphic(new FontIcon(current.open() ? Feather.LINK : Feather.LINK_2));
            button.setTooltip(new Tooltip((current.open() ? "Target: " : "Closed, choose another: ")
                    + current.id() + "  " + current.route()));
        };
        target.addListener((obs, old, now) -> show.run());
        active.addListener((ListChangeListener<ConnectionSummary>) change -> show.run());
        show.run();
        button.setOnAction(event -> {
            Popover popover = popover("Send to the " + peer + " of…", Popover.ArrowLocation.BOTTOM_LEFT);
            popover.setContentNode(list(active, target, choice -> {
                target.set(choice);
                popover.hide();
            }, clock));
            // shown from the row, not the button: the button's .mono class would cascade into the popover
            popover.show(button.getParent() == null ? button : button.getParent());
        });
        return button;
    }

    /** Read-only list for the header's connection tile. */
    static void showReadOnly(Node owner, ObservableList<ConnectionSummary> active, Clock clock) {
        Popover popover = popover("Open connections · client → proxy → destination", Popover.ArrowLocation.TOP_LEFT);
        popover.setContentNode(list(active, null, null, clock));
        popover.show(owner);
    }

    private static Popover popover(String title, Popover.ArrowLocation arrow) {
        Popover popover = new Popover();
        popover.setTitle(title);
        popover.setHeaderAlwaysVisible(true);
        popover.setDetachable(false);
        popover.setArrowLocation(arrow);
        popover.setAutoHide(true);
        return popover;
    }

    private static Node list(ObservableList<ConnectionSummary> active, ObjectProperty<ConnectionSummary> target,
                             Consumer<ConnectionSummary> onPick, Clock clock) {
        CustomTextField search = new CustomTextField();
        search.setPromptText("Search #id, IP or port");
        search.setLeft(new FontIcon(Feather.SEARCH));

        ObservableList<ConnectionSummary> rows = FXCollections.observableArrayList();
        Runnable rebuild = () -> {
            List<ConnectionSummary> all = new ArrayList<>();
            if (target != null && target.get() != null && !target.get().open()) {
                all.add(target.get());
            }
            all.addAll(active);
            rows.setAll(all);
        };
        rebuild.run();
        ListChangeListener<ConnectionSummary> follow = change -> rebuild.run();
        active.addListener(follow);

        FilteredList<ConnectionSummary> shown = new FilteredList<>(rows);
        search.textProperty().addListener((obs, old, now) -> shown.setPredicate(row -> row.matches(now)));

        ListView<ConnectionSummary> view = new ListView<>(shown);
        view.getStyleClass().addAll("connection-list", Styles.DENSE);
        view.setPrefSize(560, 240);
        Label empty = new Label(active.isEmpty() ? "No open connections" : "No connection matches the search");
        empty.getStyleClass().add("hint");
        view.setPlaceholder(empty);
        ConnectionSummary chosen = target == null ? null : target.get();
        view.setCellFactory(list -> new Row(chosen, onPick != null, clock));
        if (onPick != null) {
            view.setOnMouseClicked(event -> pick(view, onPick));
            view.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    pick(view, onPick);
                }
            });
            search.setOnAction(event -> {
                if (shown.size() == 1 && shown.getFirst().open()) {
                    onPick.accept(shown.getFirst());
                }
            });
        }
        Label hint = new Label(onPick == null
                ? "The proxy column is the address clients must connect to."
                : "Enter picks the only match. Closed connections disappear once you choose another.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        hint.setMaxWidth(560);

        VBox box = new VBox(8, search, view, hint);
        box.getStyleClass().add("connection-picker-pane");
        box.sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) {
                active.removeListener(follow);
            } else {
                Platform.runLater(search::requestFocus);
            }
        });
        return box;
    }

    private static void pick(ListView<ConnectionSummary> view, Consumer<ConnectionSummary> onPick) {
        ConnectionSummary row = view.getSelectionModel().getSelectedItem();
        if (row != null && row.open()) {
            onPick.accept(row);
        }
    }

    /** id · age on the first line, the route on the second; the proxy hop is dimmed. */
    private static final class Row extends ListCell<ConnectionSummary> {
        private final ConnectionSummary chosen;
        private final boolean selectable;
        private final Clock clock;
        private final Label id = new Label();
        private final Label age = new Label();
        private final Label client = new Label();
        private final Label proxy = new Label();
        private final Label destination = new Label();
        private final VBox content;

        Row(ConnectionSummary chosen, boolean selectable, Clock clock) {
            this.chosen = chosen;
            this.selectable = selectable;
            this.clock = clock;
            id.getStyleClass().add("connection-id");
            age.getStyleClass().add("hint");
            client.getStyleClass().addAll("mono", "connection-client");
            proxy.getStyleClass().addAll("mono", "connection-proxy");
            destination.getStyleClass().addAll("mono", "connection-destination");
            for (Label label : new Label[] {client, proxy, destination}) {
                label.setMinWidth(Label.USE_PREF_SIZE);
            }
            HBox top = new HBox(8, id, new Spacer(), age);
            top.setAlignment(Pos.CENTER_LEFT);
            HBox route = new HBox(6, client, arrow(), proxy, arrow(), destination);
            route.setAlignment(Pos.CENTER_LEFT);
            content = new VBox(2, top, route);
            HBox.setHgrow(content, Priority.ALWAYS);
            setText(null);
        }

        private static Label arrow() {
            Label arrow = new Label("→");
            arrow.getStyleClass().add("connection-proxy");
            return arrow;
        }

        @Override
        protected void updateItem(ConnectionSummary item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            boolean isChosen = chosen != null && chosen.id().equals(item.id());
            id.setText(item.id() + (isChosen ? (item.open() ? "  · current target" : "  · closed") : ""));
            age.setText(item.open() ? "open " + Formats.duration(Duration.between(item.openedAt(), clock.instant()))
                    : "closed");
            client.setText(item.client().toString());
            proxy.setText(item.proxy().toString());
            destination.setText(item.destination().map(Object::toString).orElse("connecting…"));
            pseudoClassStateChanged(CLOSED, !item.open());
            setDisable(selectable && !item.open());
            setGraphic(content);
        }
    }
}
