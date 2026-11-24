package io.github.mralireza82.wirelens.adapter.javafx.view;

import atlantafx.base.controls.Spacer;
import atlantafx.base.theme.Styles;
import io.github.mralireza82.wirelens.adapter.javafx.util.Formats;
import io.github.mralireza82.wirelens.adapter.javafx.viewmodel.MainViewModel;
import io.github.mralireza82.wirelens.application.proxy.ProxyState;
import io.github.mralireza82.wirelens.application.proxy.ProxyStatus;
import io.github.mralireza82.wirelens.application.statistics.StatisticsSnapshot;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * The header: identity, relay status and route, the start/stop control, app menu, and live statistics tiles.
 *
 * <p>Its height follows its content (about 130 px) instead of a fixed percentage of the window: a percentage
 * wastes space on tall screens and clips the tiles on short ones.
 */
final class HeaderView extends VBox {
    private final MainViewModel model;
    private final Label statusPill = new Label();
    private final Label route = new Label();
    private final Button startStop = new Button();

    HeaderView(MainViewModel model, Runnable openCapture, Runnable exportAll, Runnable showAbout) {
        this.model = model;
        getStyleClass().add("header");
        getChildren().addAll(titleRow(openCapture, exportAll, showAbout), tilesRow());
        model.status().addListener((obs, old, now) -> showStatus(now));
        showStatus(model.status().get());
    }

    private HBox titleRow(Runnable openCapture, Runnable exportAll, Runnable showAbout) {
        FontIcon logo = new FontIcon(Feather.APERTURE);
        logo.getStyleClass().add("logo");
        Label title = new Label("WireLens");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("TCP relay & traffic inspector");
        subtitle.getStyleClass().add("app-subtitle");
        VBox identity = new VBox(-2, title, subtitle);

        statusPill.getStyleClass().add("status-pill");
        statusPill.setMinWidth(Label.USE_PREF_SIZE);
        route.getStyleClass().addAll("route", "mono");
        route.setMinWidth(0);

        startStop.setDefaultButton(false);
        startStop.setMinWidth(Button.USE_PREF_SIZE);
        startStop.setOnAction(event -> model.toggleRunning());
        startStop.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            ProxyState state = model.status().get().state();
            return state == ProxyState.STARTING || state == ProxyState.STOPPING
                    || (state.canStart() && !model.canStart().get());
        }, model.status(), model.canStart()));
        startStop.setTooltip(new Tooltip("Start or stop the relay (F5)"));

        Button theme = new Button();
        theme.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        theme.graphicProperty().bind(Bindings.createObjectBinding(
                () -> new FontIcon(model.darkTheme().get() ? Feather.SUN : Feather.MOON), model.darkTheme()));
        theme.setTooltip(new Tooltip("Switch light / dark theme"));
        theme.setOnAction(event -> {
            model.darkTheme().set(!model.darkTheme().get());
            model.saveSettings();
        });

        MenuItem open = new MenuItem("Open capture…", new FontIcon(Feather.FOLDER));
        open.setOnAction(event -> openCapture.run());
        MenuItem export = new MenuItem("Export everything…", new FontIcon(Feather.DOWNLOAD));
        export.setOnAction(event -> exportAll.run());
        MenuItem reset = new MenuItem("Reset statistics", new FontIcon(Feather.REFRESH_CW));
        reset.setOnAction(event -> model.resetStatistics());
        MenuItem clear = new MenuItem("Clear both panels", new FontIcon(Feather.TRASH_2));
        clear.setOnAction(event -> model.clearAll());
        MenuItem about = new MenuItem("About WireLens", new FontIcon(Feather.INFO));
        about.setOnAction(event -> showAbout.run());
        MenuButton menu = new MenuButton(null, new FontIcon(Feather.MENU), open, export, new SeparatorMenuItem(),
                reset, clear, new SeparatorMenuItem(), about);
        menu.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, "no-arrow");
        menu.setTooltip(new Tooltip("Menu"));

        HBox row = new HBox(12, logo, identity, new Spacer(), route, statusPill, startStop, theme, menu);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(route, Priority.SOMETIMES);
        return row;
    }

    private HBox tilesRow() {
        StatTile connections = new StatTile("Connections", icon(Feather.LINK));
        StatTile upstream = new StatTile("Client → Destination", icon(Feather.ARROW_UP_RIGHT));
        StatTile downstream = new StatTile("Destination → Client", icon(Feather.ARROW_DOWN_LEFT));
        StatTile totals = new StatTile("Received / Sent", icon(Feather.BAR_CHART_2));
        StatTile errors = new StatTile("Errors", icon(Feather.ALERT_TRIANGLE));
        StatTile uptime = new StatTile("Uptime", icon(Feather.CLOCK));

        Sparkline sparkline = new Sparkline(model.upstreamHistory(), model.downstreamHistory(),
                MainViewModel.THROUGHPUT_SAMPLES);
        Label upLegend = new Label();
        upLegend.getStyleClass().add("legend-up");
        Label downLegend = new Label();
        downLegend.getStyleClass().add("legend-down");
        upLegend.textProperty().bind(Bindings.createStringBinding(
                () -> "▲ " + Formats.rate(model.upstreamRate().get()), model.upstreamRate()));
        downLegend.textProperty().bind(Bindings.createStringBinding(
                () -> "▼ " + Formats.rate(model.downstreamRate().get()), model.downstreamRate()));
        Label throughputCaption = new Label("Throughput (last minute)", icon(Feather.ACTIVITY));
        throughputCaption.getStyleClass().add("stat-label");
        HBox legend = new HBox(10, upLegend, downLegend);
        VBox throughput = new VBox(2, new HBox(8, throughputCaption, new Spacer(), legend), sparkline);
        VBox.setVgrow(sparkline, Priority.ALWAYS);
        throughput.getStyleClass().add("stat-tile");
        throughput.setMinWidth(200);
        throughput.setPrefWidth(320);
        HBox.setHgrow(throughput, Priority.ALWAYS);

        model.statistics().addListener((obs, old, now) -> showStatistics(now, connections, upstream, downstream,
                totals, errors));
        showStatistics(model.statistics().get(), connections, upstream, downstream, totals, errors);
        uptime.value.textProperty().bind(model.uptime());
        uptime.detail.textProperty().bind(Bindings.createStringBinding(() -> {
            ProxyStatus status = model.status().get();
            return status.state() == ProxyState.RUNNING ? "since " + Formats.time(status.since()).substring(0, 8)
                    : "not running";
        }, model.status()));

        HBox row = new HBox(10, connections, upstream, downstream, totals, errors, uptime, throughput);
        row.setFillHeight(true);
        return row;
    }

    private void showStatistics(StatisticsSnapshot stats, StatTile connections, StatTile upstream,
                                StatTile downstream, StatTile totals, StatTile errors) {
        connections.value.setText(stats.activeConnections() + " active");
        connections.detail.setText(Formats.count(stats.totalConnections()) + " total · peak "
                + stats.peakConnections());
        upstream.value.setText(Formats.bytes(stats.bytesFromClients()));
        upstream.detail.setText("forwarded " + Formats.bytes(stats.bytesToDestination()));
        downstream.value.setText(Formats.bytes(stats.bytesFromDestination()));
        downstream.detail.setText("forwarded " + Formats.bytes(stats.bytesToClients()));
        totals.value.setText(Formats.bytes(stats.totalBytesReceived()) + " / " + Formats.bytes(stats.totalBytesSent()));
        totals.detail.setText(Formats.count(stats.dataEvents()) + " data events");
        errors.value.setText(Formats.count(stats.failures()));
        errors.detail.setText(stats.failures() == 0 ? "all good" : "see the panels");
        errors.getStyleClass().remove("alert");
        if (stats.failures() > 0) {
            errors.getStyleClass().add("alert");
        }
    }

    private void showStatus(ProxyStatus status) {
        statusPill.getStyleClass().removeAll("running", "stopped", "busy", "failed");
        FontIcon dot = new FontIcon(Feather.CIRCLE);
        statusPill.setGraphic(dot);
        switch (status.state()) {
            case RUNNING -> {
                statusPill.setText("Running");
                statusPill.getStyleClass().add("running");
            }
            case STARTING -> {
                statusPill.setText("Starting…");
                statusPill.getStyleClass().add("busy");
            }
            case STOPPING -> {
                statusPill.setText("Stopping…");
                statusPill.getStyleClass().add("busy");
            }
            case FAILED -> {
                statusPill.setText("Failed");
                statusPill.getStyleClass().add("failed");
            }
            case STOPPED -> {
                statusPill.setText("Stopped");
                statusPill.getStyleClass().add("stopped");
            }
        }
        statusPill.setTooltip(status.error().map(Tooltip::new).orElse(null));

        String listen = status.listen().map(Object::toString).orElse("not listening");
        String destination = status.destination().map(Object::toString).orElse("no destination");
        route.setText(status.state() == ProxyState.RUNNING ? listen + "  →  " + destination : "");
        route.setTooltip(new Tooltip("Clients connect to " + listen + " and are forwarded to " + destination));

        boolean active = status.state() == ProxyState.RUNNING || status.state() == ProxyState.STOPPING;
        startStop.setText(active ? "Stop" : "Start");
        startStop.setGraphic(new FontIcon(active ? Feather.SQUARE : Feather.PLAY));
        startStop.getStyleClass().removeAll(Styles.ACCENT, Styles.DANGER);
        startStop.getStyleClass().add(active ? Styles.DANGER : Styles.ACCENT);
    }

    private static FontIcon icon(Feather glyph) {
        FontIcon icon = new FontIcon(glyph);
        icon.setIconSize(12);
        icon.getStyleClass().add("tile-icon");
        return icon;
    }
}
