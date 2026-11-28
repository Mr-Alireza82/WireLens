package io.github.mralireza82.wirelens.relay.domain.traffic;

/** The kinds of traffic event, used for filtering and display. */
public enum EventKind {
    OPENED,
    DATA,
    STREAM_ENDED,
    CLOSED,
    FAILED,
    NOTICE
}
