package io.github.mralireza82.wirelens.application.event;

import io.github.mralireza82.wirelens.application.port.out.TrafficEventSink;
import io.github.mralireza82.wirelens.domain.traffic.TrafficEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fans one event out to several sinks (Composite + Observer). A failing subscriber is logged and isolated, so it
 * can never break the relay or starve the other subscribers.
 */
public final class CompositeEventSink implements TrafficEventSink {
    private static final System.Logger LOG = System.getLogger(CompositeEventSink.class.getName());

    private final List<TrafficEventSink> sinks = new CopyOnWriteArrayList<>();

    public CompositeEventSink(TrafficEventSink... initial) {
        sinks.addAll(List.of(initial));
    }

    public void add(TrafficEventSink sink) {
        sinks.add(sink);
    }

    public void remove(TrafficEventSink sink) {
        sinks.remove(sink);
    }

    @Override
    public void publish(TrafficEvent event) {
        for (TrafficEventSink sink : sinks) {
            try {
                sink.publish(event);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR, "Event subscriber " + sink + " failed", e);
            }
        }
    }
}
