package io.github.mralireza82.wirelens.adapter.gui.javafx.view;

import io.github.mralireza82.wirelens.adapter.gui.javafx.viewmodel.MainViewModel;
import io.github.mralireza82.wirelens.inspection.application.port.out.CaptureExporter;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.function.Supplier;

/**
 * The window layout: header across the top, the source and destination panels side by side below it, divided
 * 50/50 by a draggable splitter. Each panel also has its own vertical splitter between list and inspector.
 */
public final class MainView extends StackPane {
    private final MainViewModel model;
    private final Supplier<Window> window;
    private final TrafficPanelView source;
    private final TrafficPanelView destination;

    public MainView(MainViewModel model, Supplier<Window> window) {
        this.model = model;
        this.window = window;
        source = new TrafficPanelView(model.source(), model);
        destination = new TrafficPanelView(model.destination(), model);

        SplitPane panels = new SplitPane(source, destination);
        panels.setOrientation(Orientation.HORIZONTAL);
        panels.setDividerPositions(0.5);

        BorderPane layout = new BorderPane();
        layout.setTop(new HeaderView(model, this::openCapture, this::exportAll, this::showAbout));
        layout.setCenter(panels);
        getChildren().add(0, layout);
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
