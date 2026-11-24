package io.github.mralireza82.wirelens.domain.traffic;

/** Where in a connection's life a failure happened. */
public enum FailureStage {
    LISTEN,
    ACCEPT,
    CONNECT,
    READ,
    WRITE,
    SEND
}
