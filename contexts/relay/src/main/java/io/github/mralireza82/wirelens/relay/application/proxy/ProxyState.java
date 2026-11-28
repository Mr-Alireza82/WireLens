package io.github.mralireza82.wirelens.relay.application.proxy;

/** Life cycle of the relay. */
public enum ProxyState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    /** The last start attempt failed (e.g. the port was in use). Behaves like {@link #STOPPED}. */
    FAILED;

    public boolean canStart() {
        return this == STOPPED || this == FAILED;
    }

    public boolean isActive() {
        return this == STARTING || this == RUNNING || this == STOPPING;
    }
}
