package io.github.mralireza82.wirelens.adapter.javafx.viewmodel;

import io.github.mralireza82.wirelens.adapter.javafx.AppDependencies;
import io.github.mralireza82.wirelens.adapter.javafx.util.Formats;
import io.github.mralireza82.wirelens.application.port.out.CaptureExporter;
import io.github.mralireza82.wirelens.application.proxy.ProxyState;
import io.github.mralireza82.wirelens.application.proxy.ProxyStatus;
import io.github.mralireza82.wirelens.application.settings.UserSettings;
import io.github.mralireza82.wirelens.application.statistics.StatisticsSnapshot;
import io.github.mralireza82.wirelens.domain.net.Endpoint;
import io.github.mralireza82.wirelens.domain.traffic.ConnectionId;
import io.github.mralireza82.wirelens.domain.traffic.Payload;
import io.github.mralireza82.wirelens.domain.traffic.Side;
import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * The window's state: relay status and controls, live statistics for the header, the two panels, and app-wide
 * commands (export, open, theme, reset). Use on the FX thread.
 */
public final class MainViewModel {
    public static final int THROUGHPUT_SAMPLES = 120;
    /** Payload bytes each panel keeps (source and destination share most payloads, so this is not doubled). */
    public static final long PANEL_BYTE_LIMIT = 96L * 1024 * 1024;

    private final AppDependencies dependencies;
    private final Executor background;
    private final Executor ui;
    private final Notifier notifier;

    private final ObservableSet<ConnectionId> openConnections = FXCollections.observableSet();
    /** Open connections in id order, with their full route, for the header list and the target pickers. */
    private final ObservableList<ConnectionSummary> activeConnections = FXCollections.observableArrayList();
    private final Map<ConnectionId, ConnectionSummary> connectionsById = new HashMap<>();
    private final TrafficPanelViewModel source;
    private final TrafficPanelViewModel destination;
    private final ObjectProperty<ProxyStatus> status;
    private final ObjectProperty<StatisticsSnapshot> statistics;
    private final DoubleProperty upstreamRate = new SimpleDoubleProperty();
    private final DoubleProperty downstreamRate = new SimpleDoubleProperty();
    private final ObservableList<Double> upstreamHistory = FXCollections.observableArrayList();
    private final ObservableList<Double> downstreamHistory = FXCollections.observableArrayList();
    private final StringProperty uptime = new SimpleStringProperty("--:--");
    private final BooleanProperty darkTheme;
    private final BooleanBinding running;
    private final BooleanBinding canStart;
    private final BooleanBinding canApplyDestination;
    private final Deque<StatisticsSnapshot> recent = new ArrayDeque<>();

    public MainViewModel(AppDependencies dependencies, Executor background, Executor ui, Notifier notifier) {
        this.dependencies = dependencies;
        this.background = background;
        this.ui = ui;
        this.notifier = notifier;
        UserSettings saved = dependencies.settings().load();

        source = new TrafficPanelViewModel(Side.SOURCE,
                new EndpointInput(saved.listenAddress(), saved.listenPort()), saved.sourceView(),
                saved.historyLimit(), PANEL_BYTE_LIMIT, dependencies.connections(), dependencies.hold(), openConnections,
                background, ui, notifier);
        destination = new TrafficPanelViewModel(Side.DESTINATION,
                new EndpointInput(saved.destinationAddress(), saved.destinationPort()), saved.destinationView(),
                saved.historyLimit(), PANEL_BYTE_LIMIT, dependencies.connections(), dependencies.hold(), openConnections,
                background, ui, notifier);
        darkTheme = new SimpleBooleanProperty(saved.darkTheme());
        source.defaultCopyTarget(() -> destination.endpoint().endpoint());
        destination.defaultCopyTarget(() -> destination.endpoint().endpoint());

        status = new SimpleObjectProperty<>(dependencies.proxy().status());
        statistics = new SimpleObjectProperty<>(dependencies.statistics().snapshot());
        dependencies.proxy().onStatusChanged(newStatus -> ui.execute(() -> onStatus(newStatus)));
        dependencies.hold().onChange(() -> ui.execute(this::showHold));
        showHold();

        running = Bindings.createBooleanBinding(() -> status.get().state() == ProxyState.RUNNING, status);
        canStart = Bindings.createBooleanBinding(() -> status.get().state().canStart(), status)
                .and(source.endpoint().valid()).and(destination.endpoint().valid());
        canApplyDestination = Bindings.createBooleanBinding(() -> destination.endpoint().endpoint()
                        .map(endpoint -> !status.get().destination().equals(Optional.of(endpoint))).orElse(false)
                        && status.get().state() == ProxyState.RUNNING,
                status, destination.endpoint().address(), destination.endpoint().port());
    }

    /** Feeds a batch of events (from the FX bridge) into both panels. */
    public void accept(List<TrafficEvent> batch) {
        for (TrafficEvent event : batch) {
            if (event instanceof TrafficEvent.ConnectionOpened opened) {
                opened(opened);
            } else if (event instanceof TrafficEvent.ConnectionClosed closed && closed.side() == Side.SOURCE) {
                closed(closed.link().id());
            }
        }
        source.accept(batch);
        destination.accept(batch);
    }

    private void opened(TrafficEvent.ConnectionOpened opened) {
        ConnectionId id = opened.link().id();
        if (opened.side() == Side.SOURCE) {
            ConnectionSummary summary = new ConnectionSummary(id, opened.link().remote(), opened.link().local(),
                    Optional.empty(), opened.timestamp(), true);
            connectionsById.put(id, summary);
            activeConnections.add(summary);
            openConnections.add(id);
            for (TrafficPanelViewModel panel : List.of(source, destination)) {
                if (panel.target().get() == null) {
                    panel.target().set(summary); // first connection: nothing to choose from yet
                }
            }
        } else {
            ConnectionSummary known = connectionsById.get(id);
            if (known != null) {
                replace(known, known.withDestination(opened.link().remote()));
            }
        }
    }

    private void closed(ConnectionId id) {
        openConnections.remove(id);
        ConnectionSummary known = connectionsById.remove(id);
        if (known != null) {
            activeConnections.remove(known);
            ConnectionSummary gone = known.closed();
            for (TrafficPanelViewModel panel : List.of(source, destination)) {
                if (panel.target().get() != null && panel.target().get().id().equals(id)) {
                    panel.target().set(gone); // stays visible, greyed, until the user picks another
                }
            }
        }
    }

    private void replace(ConnectionSummary old, ConnectionSummary updated) {
        connectionsById.put(updated.id(), updated);
        int index = activeConnections.indexOf(old);
        if (index >= 0) {
            activeConnections.set(index, updated);
        }
        for (TrafficPanelViewModel panel : List.of(source, destination)) {
            if (old.equals(panel.target().get())) {
                panel.target().set(updated);
            }
        }
    }

    private void showHold() {
        List<io.github.mralireza82.wirelens.domain.intercept.HeldMessage> held = dependencies.hold().held();
        source.showHold(dependencies.hold().isHolding(Side.SOURCE), held);
        destination.showHold(dependencies.hold().isHolding(Side.DESTINATION), held);
    }

    private void onStatus(ProxyStatus newStatus) {
        status.set(newStatus);
        if (!newStatus.state().isActive()) {
            for (ConnectionId id : List.copyOf(openConnections)) {
                closed(id);
            }
            openConnections.clear();
        }
        if (newStatus.state() == ProxyState.FAILED) {
            notifier.notify("Could not start: " + newStatus.error().orElse("unknown error"), true);
        }
    }

    // ------------------------------------------------------------------ commands

    public void start() {
        Optional<Endpoint> listen = source.endpoint().endpoint();
        Optional<Endpoint> target = destination.endpoint().endpoint();
        if (!canStart.get() || listen.isEmpty() || target.isEmpty()) {
            return;
        }
        background.execute(() -> dependencies.proxy().start(listen.get(), target.get()));
        saveSettings();
    }

    public void stop() {
        background.execute(() -> dependencies.proxy().stop());
    }

    public void toggleRunning() {
        if (running.get()) {
            stop();
        } else {
            start();
        }
    }

    public void applyDestination() {
        destination.endpoint().endpoint().ifPresent(endpoint -> {
            background.execute(() -> dependencies.proxy().changeDestination(endpoint));
            saveSettings();
        });
    }

    /** Samples the statistics; called by the view a few times per second. */
    public void tick() {
        StatisticsSnapshot now = dependencies.statistics().snapshot();
        statistics.set(now);
        recent.addLast(now);
        while (recent.size() > 3) {
            recent.removeFirst();
        }
        StatisticsSnapshot earlier = recent.peekFirst();
        double up = now.upstreamRate(earlier);
        double down = now.downstreamRate(earlier);
        upstreamRate.set(up);
        downstreamRate.set(down);
        push(upstreamHistory, up);
        push(downstreamHistory, down);
        ProxyStatus current = status.get();
        uptime.set(current.state() == ProxyState.RUNNING
                ? Formats.duration(Duration.between(current.since(), dependencies.clock().instant())) : "--:--");
    }

    private static void push(ObservableList<Double> history, double value) {
        if (history.size() >= THROUGHPUT_SAMPLES) {
            history.remove(0, history.size() - THROUGHPUT_SAMPLES + 1);
        }
        history.add(value);
    }

    public void resetStatistics() {
        dependencies.statistics().resetTotals();
        recent.clear();
        upstreamHistory.clear();
        downstreamHistory.clear();
        tick();
    }

    public void clearAll() {
        source.clear();
        destination.clear();
    }

    /** Exports both panels, merged in time order. */
    public void exportAll(CaptureExporter exporter, Path target) {
        List<TrafficEvent> all = new ArrayList<>(source.events());
        all.addAll(destination.events());
        all.sort(Comparator.comparing(TrafficEvent::timestamp));
        export(exporter, all, target);
    }

    /** Exports what one panel currently shows. */
    public void exportPanel(TrafficPanelViewModel panel, CaptureExporter exporter, Path target) {
        export(exporter, panel.visibleEvents(), target);
    }

    private void export(CaptureExporter exporter, List<TrafficEvent> events, Path target) {
        background.execute(() -> {
            try {
                exporter.export(events, target);
                ui.execute(() -> notifier.notify("Exported " + Formats.count(events.size()) + " events to "
                        + target.getFileName(), false));
            } catch (IOException | RuntimeException e) {
                ui.execute(() -> notifier.notify("Export failed: " + e.getMessage(), true));
            }
        });
    }

    public void openCapture(Path file) {
        background.execute(() -> {
            try {
                List<TrafficEvent> events = dependencies.importer().load(file);
                ui.execute(() -> {
                    source.importEvents(events);
                    destination.importEvents(events);
                    notifier.notify("Loaded " + Formats.count(events.size()) + " events from " + file.getFileName(),
                            false);
                });
            } catch (IOException | RuntimeException e) {
                ui.execute(() -> notifier.notify("Could not open capture: " + e.getMessage(), true));
            }
        });
    }

    public void saveRawPayload(Payload payload, Path target) {
        background.execute(() -> {
            try {
                dependencies.payloadWriter().write(payload, target);
                ui.execute(() -> notifier.notify("Saved " + payload.size() + " raw bytes to " + target.getFileName(),
                        false));
            } catch (IOException e) {
                ui.execute(() -> notifier.notify("Could not save: " + e.getMessage(), true));
            }
        });
    }

    public void saveSettings() {
        UserSettings current = dependencies.settings().load()
                .withListen(source.endpoint().address().get(), source.endpoint().port().get())
                .withDestination(destination.endpoint().address().get(), destination.endpoint().port().get())
                .withViews(source.settings().get(), destination.settings().get())
                .withDarkTheme(darkTheme.get());
        background.execute(() -> dependencies.settings().save(current));
    }

    // ------------------------------------------------------------------ accessors

    public TrafficPanelViewModel source() {
        return source;
    }

    public TrafficPanelViewModel destination() {
        return destination;
    }

    public ObjectProperty<ProxyStatus> status() {
        return status;
    }

    public ObjectProperty<StatisticsSnapshot> statistics() {
        return statistics;
    }

    public DoubleProperty upstreamRate() {
        return upstreamRate;
    }

    public DoubleProperty downstreamRate() {
        return downstreamRate;
    }

    public ObservableList<Double> upstreamHistory() {
        return upstreamHistory;
    }

    public ObservableList<Double> downstreamHistory() {
        return downstreamHistory;
    }

    public StringProperty uptime() {
        return uptime;
    }

    public BooleanProperty darkTheme() {
        return darkTheme;
    }

    public BooleanBinding running() {
        return running;
    }

    public BooleanBinding canStart() {
        return canStart;
    }

    public BooleanBinding canApplyDestination() {
        return canApplyDestination;
    }

    public ObservableSet<ConnectionId> openConnections() {
        return openConnections;
    }

    public ObservableList<ConnectionSummary> activeConnections() {
        return activeConnections;
    }

    public java.time.Clock clock() {
        return dependencies.clock();
    }

    public List<CaptureExporter> exporters() {
        return dependencies.exporters();
    }

    public String importFormatName() {
        return dependencies.importer().displayName();
    }

    public String importExtension() {
        return dependencies.importer().fileExtension();
    }
}
