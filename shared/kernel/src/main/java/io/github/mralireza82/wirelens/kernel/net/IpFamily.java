package io.github.mralireza82.wirelens.kernel.net;

/** The two Internet Protocol address families. */
public enum IpFamily {
    IPV4(4),
    IPV6(16);

    private final int byteLength;

    IpFamily(int byteLength) {
        this.byteLength = byteLength;
    }

    /** Number of bytes in an address of this family: 4 or 16. */
    public int byteLength() {
        return byteLength;
    }
}
