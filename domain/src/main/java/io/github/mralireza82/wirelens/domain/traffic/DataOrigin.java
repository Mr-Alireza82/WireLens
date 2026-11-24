package io.github.mralireza82.wirelens.domain.traffic;

/** Why bytes were sent: forwarded from the other leg, or typed in by the user. */
public enum DataOrigin {
    RELAYED,
    INJECTED
}
