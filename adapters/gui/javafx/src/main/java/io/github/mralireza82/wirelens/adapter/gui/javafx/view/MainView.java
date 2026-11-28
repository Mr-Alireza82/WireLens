package io.github.mralireza82.wirelens.adapter.gui.javafx.view;

import io.github.mralireza82.wirelens.adapter.gui.javafx.viewmodel.MainViewModel;
import io.github.mralireza82.wirelens.inspection.application.port.out.CaptureExporter;
import io.github.mralireza82.wirelens.presentation.layout.Arrangement;
import io.github.mralireza82.wirelens.presentation.layout.WindowSizeClass;
import io.github.mralireza82.wirelens.presentation.traffic.layout.TrafficScreenLayout;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.function.Supplier;

/**
 * The window layout: header across the top, the source and destination panels below it. How the panels share the
 * window is not decided here: {@link TrafficScreenLayout} (presentation layer) picks it from the window's size
 * class, and this view only draws it, re-arranging live as the window is resized:
 * side by side or stacked in a draggable {@link SplitPane}, or one at a time in tabs.
 */
public final class MainView extends StackPane {
    private final MainViewModel model;
    private final Supplier<Window> window;
    private final TrafficPanelView source;
    private final TrafficPanelView destination;
    private final HeaderView header;
    private final SplitPane split = new SplitPane();
    private final TabPane tabs = new TabPane();
    private final Tab sourceTab = new Tab("Source (client)");
    private final Tab destinationTab = new Tab("Destination");
    private final BorderPane layout = new BorderPane();
    private final ObjectProperty<TrafficScreenLayout> currentLayout = new SimpleObjectProperty<>();

    public MainView(MainViewModel model, Supplier<Window> window) {
        this.model = model;
        this.window = window;
        source = new TrafficPanelView(model.source(), model);
        destination = new TrafficPanelView(model.destination(), model);

        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().setAll(sourceTab, destinationTab);
        tabs.getStyleClass().add("panel-tabs");
        header = new HeaderView(model, this::openCapture, this::exportAll, this::showAbout);
        layout.setTop(header);
        layout.setMinSize(0, 0);
        getChildren().add(0, layout);
        apply(TrafficScreenLayout.forSize(WindowSizeClass.of(1440, 900)));

        // The window (scene) size decides, not this view's own size: content may ask for more room than the window
        // has. Re-evaluated on every resize; only a change of size class re-arranges, so dragging stays smooth.
        javafx.beans.value.ChangeListener<Number> resize = (obs, old, now) -> resized();
        sceneProperty().addListener((obs, old, scene) -> {
            if (old != null) {
                old.widthProperty().removeListener(resize);
                old.heightProperty().removeListener(resize);
            }
            if (scene != null) {
                scene.widthProperty().addListener(resize);
                scene.heightProperty().addListener(resize);
                resized();
            }
        });
        setMinSize(0, 0);
    }

    /** The layout currently shown (for tests and screenshots). */
    public ReadOnlyObjectProperty<TrafficScreenLayout> currentLayout() {
        return currentLayout;
    }

    private void resized() {
        javafx.scene.Scene scene = getScene();
        if (scene != null && scene.getWidth() > 0 && scene.getHeight() > 0) {
            apply(TrafficScreenLayout.forSize(WindowSizeClass.of(scene.getWidth(), scene.getHeight())));
        }
    }

    /** Draws the given layout. Panels are moved between containers, never re-created, so no state is lost. */
    public void apply(TrafficScreenLayout next) {
        if (next.equals(currentLayout.get())) {
            return;
        }
        currentLayout.set(next);
        header.applyDensity(next.header());
        source.applyLayout(next.listAndInspector(), next.listSplit());
        destination.applyLayout(next.listAndInspector(), next.listSplit());

        split.getItems().clear();
        sourceTab.setContent(null);
        destinationTab.setContent(null);
        if (next.panels() == Arrangement.ONE_AT_A_TIME) {
            for (TrafficPanelView panel : new TrafficPanelView[] {source, destination}) {
                panel.setMinWidth(0);
                panel.setMinHeight(0);
            }
            sourceTab.setContent(source);
            destinationTab.setContent(destination);
            layout.setCenter(tabs);
        } else {
            boolean sideBySide = next.panels() == Arrangement.SIDE_BY_SIDE;
            split.setOrientation(sideBySide ? Orientation.HORIZONTAL : Orientation.VERTICAL);
            source.setMinWidth(sideBySide ? next.panelSplit().firstMinDp() : 0);
            destination.setMinWidth(sideBySide ? next.panelSplit().secondMinDp() : 0);
            source.setMinHeight(0);
            destination.setMinHeight(0);
            split.getItems().setAll(source, destination);
            split.setDividerPositions(next.panelSplit().firstFraction());
            layout.setCenter(split);
        }
    }

    public void openCapture() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open a WireLens capture");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(model.importFormatName(),
                "*." + model.importExtension()));
        File file = chooser.showOpenDialog(window.get());
        if (file != null) {
            model.openCapture(file.toPath());
        }
    }

    public void exportAll() {
        ChoiceDialog<CaptureExporter> formats = new ChoiceDialog<>(model.exporters().getFirst(), model.exporters());
        formats.setTitle("Export everything");
        formats.setHeaderText("Export both panels, merged in time order");
        formats.setContentText("Format:");
        formats.initOwner(window.get());
        formats.showAndWait().ifPresent(exporter -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Export everything");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(exporter.displayName(),
                    "*." + exporter.fileExtension()));
            chooser.setInitialFileName("wirelens-capture." + exporter.fileExtension());
            File file = chooser.showSaveDialog(window.get());
            if (file != null) {
                model.exportAll(exporter, file.toPath());
            }
        });
    }

    public void showAbout() {
        Alert about = new Alert(Alert.AlertType.INFORMATION, null, ButtonType.OK);
        about.initOwner(window.get());
        about.setTitle("About WireLens");
        about.setHeaderText("WireLens 1.0 · TCP relay & traffic inspector");
        about.setContentText("""
                Forwards every client that connects to the listening address to the destination and records \
                every byte in both directions.

                Raw bytes are the source of truth: changing the encoding, base or separator re-renders the \
                view and never touches captured data.

                Shortcuts: F5 start/stop · Ctrl+O open capture · Ctrl+S export · Ctrl+L clear · Ctrl+C copy.""");
        about.showAndWait();
    }

    TrafficPanelView sourcePanel() {
        return source;
    }

    TrafficPanelView destinationPanel() {
        return destination;
    }
}
