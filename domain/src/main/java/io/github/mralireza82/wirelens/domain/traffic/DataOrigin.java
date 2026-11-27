package io.github.mralireza82.wirelens.domain.traffic;

/** Why bytes were sent: forwarded from the other leg, typed in by the user, or held and edited before forwarding. */
public enum DataOrigin {
    RELAYED,
    INJECTED,
    EDITED
}
