package io.github.mralireza82.wirelens.domain.traffic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayloadTest {

    @Test
    void neverSharesItsBytes() {
        byte[] source = {1, 2, 3};
        Payload payload = Payload.copyOf(source);
        source[0] = 9;
        payload.toByteArray()[1] = 9;
        assertThat(payload.toByteArray()).containsExactly(1, 2, 3);
        assertThat(payload.asReadOnlyBuffer().isReadOnly()).isTrue();
    }

    @Test
    void copiesARange() {
        Payload payload = Payload.copyOf(new byte[] {0, 1, 2, 3, 4}, 1, 3);
        assertThat(payload.toByteArray()).containsExactly(1, 2, 3);
        assertThat(payload.unsignedAt(0)).isEqualTo(1);
        assertThat(payload.head(2)).containsExactly(1, 2);
        assertThat(payload.head(99)).hasSize(3);
        assertThatThrownBy(() -> Payload.copyOf(new byte[2], 1, 5)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void readsBytesAsUnsigned() {
        assertThat(Payload.copyOf(new byte[] {(byte) 0xFF}).unsignedAt(0)).isEqualTo(255);
    }

    @Test
    void comparesByContent() {
        assertThat(Payload.copyOf(new byte[] {7})).isEqualTo(Payload.copyOf(new byte[] {7}));
        assertThat(Payload.copyOf(new byte[0])).isSameAs(Payload.empty());
    }
}
