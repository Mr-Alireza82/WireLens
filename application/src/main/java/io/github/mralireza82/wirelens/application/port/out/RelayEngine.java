package io.github.mralireza82.wirelens.application.port.out;

import io.github.mralireza82.wirelens.domain.proxy.ProxyConfiguration;

import java.io.IOException;

/** Starts relays. Implemented by a networking adapter (e.g. TCP). */
public interface RelayEngine {

    /**
     * Binds the listening socket and starts accepting clients in the background.
     *
     * @throws IOException when the listening socket cannot be opened (port in use, address not local, ...)
     */
    RunningRelay start(ProxyConfiguration configuration, TrafficEventSink sink) throws IOException;
}
