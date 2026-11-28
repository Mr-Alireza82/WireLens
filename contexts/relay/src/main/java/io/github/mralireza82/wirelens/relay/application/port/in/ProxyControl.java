package io.github.mralireza82.wirelens.relay.application.port.in;

import io.github.mralireza82.wirelens.relay.application.proxy.ProxyStatus;
import io.github.mralireza82.wirelens.kernel.net.Endpoint;

import java.util.function.Consumer;

/** Use case: start, stop and re-target the relay. */
public interface ProxyControl {

    /** Starts listening. Blocking but quick (binds a socket); call it off the UI thread. */
    ProxyStatus start(Endpoint listen, Endpoint destination);

    /** Stops listening and closes every connection. */
    ProxyStatus stop();

    /** Changes where new connections go. Works whether or not the relay is running. */
    void changeDestination(Endpoint destination);

    ProxyStatus status();

    /** Notified (on any thread) whenever the status changes. */
    void onStatusChanged(Consumer<ProxyStatus> listener);
}
