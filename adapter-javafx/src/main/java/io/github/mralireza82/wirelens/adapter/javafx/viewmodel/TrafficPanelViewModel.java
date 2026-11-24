package io.github.mralireza82.wirelens.adapter.javafx.viewmodel;

import io.github.mralireza82.wirelens.application.filter.TrafficFilter;
import io.github.mralireza82.wirelens.application.port.in.ConnectionCommands;
import io.github.mralireza82.wirelens.application.render.PayloadParser;
import io.github.mralireza82.wirelens.application.render.PayloadSyntaxException;
import io.github.mralireza82.wirelens.application.render.RenderSettings;
import io.github.mralireza82.wirelens.domain.traffic.ConnectionId;
import io.github.mralireza82.wirelens.domain.traffic.EventKind;
import io.github.mralireza82.wirelens.domain.traffic.Payload;
import io.github.mralireza82.wirelens.domain.traffic.Side;
import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.transformation.FilteredList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * State and behaviour of one traffic panel (source or destination): the bounded event history, the view
 * settings (encoding, base, separator), filtering, pausing, selection and the send box.
 *
 * <p>Must be used on the FX thread; slow work (sending bytes) runs on {@code background} and reports back through
 * {@code ui}. Both executors are injected so the class is unit-testable without a running toolkit.
 */
public final class TrafficPanelViewModel {
    private final Side side;
    private final int historyLimit;
    private final long byteLimit;
    private long retainedBytes;
    private final ConnectionCommands commands;
    private final ObservableSet<ConnectionId> openConnections;
    private final Executor background;
    private final Executor ui;
    private final Notifier notifier;
    private final PayloadParser parser = new PayloadParser();

    private final EndpointInput endpoint;
    private final ObservableList<TrafficEntry> entries = FXCollections.observableArrayList();
    private final FilteredList<TrafficEntry> visible = new FilteredList<>(entries);
    private final ObjectProperty<RenderSettings> settings;
    private final StringProperty query = new SimpleStringProperty("");
    private final ObjectProperty<Set<EventKind>> kinds = new SimpleObjectProperty<>(EnumSet.allOf(EventKind.class));
    private final BooleanProperty paused = new SimpleBooleanProperty();
    private final LongProperty skippedWhilePaused = new SimpleLongProperty();
    private final BooleanProperty followLatest = new SimpleBooleanProperty(true);
    private final ObjectProperty<TrafficEntry> selected = new SimpleObjectProperty<>();
    private final StringProperty composerInput = new SimpleStringProperty("");
    private final ObjectProperty<ComposerMode> composerMode = new SimpleObjectProperty<>(ComposerMode.NUMBERS);
    private final StringBinding composerError;
    private final ObjectBinding<Optional<ConnectionId>> composerTarget;
    private final BooleanBinding canSend;
    private long nextNumber = 1;

    /**
     * @param historyLimit most entries kept; the oldest are dropped first
     * @param byteLimit    most payload bytes kept, so a flood of large chunks cannot exhaust memory
     */
    public TrafficPanelViewModel(Side side, EndpointInput endpoint, RenderSettings initialSettings, int historyLimit,
                                 long byteLimit, ConnectionCommands commands, ObservableSet<ConnectionId> openConnections,
                                 Executor background, Executor ui, Notifier notifier) {
        this.side = Objects.requireNonNull(side, "side");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.settings = new SimpleObjectProperty<>(Objects.requireNonNull(initialSettings, "initialSettings"));
        this.historyLimit = historyLimit;
        this.byteLimit = byteLimit;
        this.commands = commands;
        this.openConnections = openConnections;
        this.background = background;
        this.ui = ui;
        this.notifier = notifier;

        query.addListener((obs, old, now) -> refilter());
        kinds.addListener((obs, old, now) -> refilter());
        settings.addListener((obs, old, now) -> {
            if (old == null || old.encoding() != now.encoding()) {
                refilter();
            }
        });

        composerError = Bindings.createStringBinding(this::validateComposer, composerInput, composerMode, settings);
        composerTarget = Bindings.createObjectBinding(() -> Optional.ofNullable(selected.get())
                .flatMap(entry -> entry.event().connectionId())
                .filter(openConnections::contains), selected, openConnections);
        canSend = Bindings.createBooleanBinding(() -> composerTarget.get().isPresent()
                && !composerInput.get().isBlank() && composerError.get().isEmpty(), composerTarget, composerInput,
                composerError);
    }

    // ------------------------------------------------------------------ capture

    /** Adds the events of a batch that belong to this side, unless paused. */
    public void accept(List<TrafficEvent> batch) {
        List<TrafficEvent> mine = batch.stream().filter(event -> event.side() == side).toList();
        if (mine.isEmpty()) {
            return;
        }
        if (paused.get()) {
            skippedWhilePaused.set(skippedWhilePaused.get() + mine.size());
            return;
        }
        append(mine);
    }

    /** Adds events loaded from a file, even while paused. */
    public void importEvents(List<TrafficEvent> events) {
        append(events.stream().filter(event -> event.side() == side).toList());
    }

    private void append(List<TrafficEvent> events) {
        RenderSettings current = settings.get();
        List<TrafficEntry> added = new ArrayList<>(events.size());
        for (TrafficEvent event : events) {
            added.add(new TrafficEntry(nextNumber++, event, current));
            retainedBytes += event.byteCount();
        }
        // Drop the oldest entries (existing ones first, then the oldest of this batch) until both limits hold.
        int total = entries.size() + added.size();
        int dropExisting = 0;
        while (dropExisting < entries.size() && (total - dropExisting > historyLimit || retainedBytes > byteLimit)) {
            retainedBytes -= entries.get(dropExisting++).event().byteCount();
        }
        int dropAdded = 0;
        if (dropExisting == entries.size()) {
            while (dropAdded < added.size() - 1
                    && (added.size() - dropAdded > historyLimit || retainedBytes > byteLimit)) {
                retainedBytes -= added.get(dropAdded++).event().byteCount();
            }
        }
        if (dropExisting == entries.size() && dropExisting > 0) {
            entries.setAll(added.subList(dropAdded, added.size()));
            return;
        }
        if (dropExisting > 0) {
            entries.remove(0, dropExisting);
        }
        entries.addAll(added.subList(dropAdded, added.size()));
    }

    /** Payload bytes currently held by this panel. */
    public long retainedBytes() {
        return retainedBytes;
    }

    public void clear() {
        entries.clear();
        retainedBytes = 0;
        skippedWhilePaused.set(0);
        selected.set(null);
    }

    public void togglePause() {
        paused.set(!paused.get());
        if (!paused.get()) {
            skippedWhilePaused.set(0);
        }
    }

    private void refilter() {
        TrafficFilter filter = new TrafficFilter(query.get(), kinds.get(), settings.get().encoding());
        visible.setPredicate(filter.isUnrestricted() ? null : entry -> filter.test(entry.event()));
    }

    /** Shows only one connection by searching for its id. */
    public void focusConnection(ConnectionId id) {
        query.set(id.toString());
    }

    // ------------------------------------------------------------------ send box

    private String validateComposer() {
        String input = composerInput.get();
        if (input == null || input.isBlank()) {
            return "";
        }
        try {
            parse(input);
            return "";
        } catch (PayloadSyntaxException e) {
            return e.getMessage();
        }
    }

    private byte[] parse(String input) {
        RenderSettings view = settings.get();
        return composerMode.get() == ComposerMode.TEXT
                ? parser.parseText(input, view.encoding())
                : parser.parseNumbers(input, view.radix());
    }

    /** Sends the parsed bytes to this side's peer of the selected connection. */
    public void send() {
        Optional<ConnectionId> target = composerTarget.get();
        if (!canSend.get() || target.isEmpty()) {
            return;
        }
        ConnectionId id = target.get();
        Payload payload = Payload.copyOf(parse(composerInput.get()));
        String peer = side == Side.SOURCE ? "client" : "destination";
        background.execute(() -> {
            try {
                commands.send(id, side, payload);
                ui.execute(() -> notifier.notify("Sent " + payload.size() + " bytes to the " + peer + " of " + id,
                        false));
            } catch (IOException e) {
                ui.execute(() -> notifier.notify("Could not send to " + id + ": " + e.getMessage(), true));
            }
        });
    }

    public void disconnectSelected() {
        Optional.ofNullable(selected.get()).flatMap(entry -> entry.event().connectionId())
                .ifPresent(id -> background.execute(() -> commands.disconnect(id)));
    }

    // ------------------------------------------------------------------ accessors

    public Side side() {
        return side;
    }

    public EndpointInput endpoint() {
        return endpoint;
    }

    public ObservableList<TrafficEntry> entries() {
        return entries;
    }

    public ObservableList<TrafficEntry> visibleEntries() {
        return visible;
    }

    public ObjectProperty<RenderSettings> settings() {
        return settings;
    }

    public StringProperty query() {
        return query;
    }

    public ObjectProperty<Set<EventKind>> kinds() {
        return kinds;
    }

    public BooleanProperty paused() {
        return paused;
    }

    public LongProperty skippedWhilePaused() {
        return skippedWhilePaused;
    }

    public BooleanProperty followLatest() {
        return followLatest;
    }

    public ObjectProperty<TrafficEntry> selected() {
        return selected;
    }

    public StringProperty composerInput() {
        return composerInput;
    }

    public ObjectProperty<ComposerMode> composerMode() {
        return composerMode;
    }

    public StringBinding composerError() {
        return composerError;
    }

    public ObjectBinding<Optional<ConnectionId>> composerTarget() {
        return composerTarget;
    }

    public BooleanBinding canSend() {
        return canSend;
    }

    public ObservableSet<ConnectionId> openConnections() {
        return openConnections;
    }

    /** All events currently held (unfiltered), oldest first. */
    public List<TrafficEvent> events() {
        return entries.stream().map(TrafficEntry::event).toList();
    }

    /** Events that pass the current filter, oldest first. */
    public List<TrafficEvent> visibleEvents() {
        return visible.stream().map(TrafficEntry::event).toList();
    }
}
