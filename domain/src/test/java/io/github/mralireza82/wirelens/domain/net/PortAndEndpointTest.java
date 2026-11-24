package io.github.mralireza82.wirelens.domain.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortAndEndpointTest {

    @ParameterizedTest
    @ValueSource(strings = {"1", "80", " 8583 ", "65535"})
    void parsesValidPorts(String text) {
        assertThat(Port.parse(text).value()).isBetween(1, 65535);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "65536", "-1", "80a", "+80", "1e3", "999999"})
    void rejectsInvalidPorts(String text) {
        assertThatThrownBy(() -> Port.parse(text)).isInstanceOf(InvalidAddressException.class);
        assertThat(Port.validate(text)).isPresent();
    }

    @Test
    void writesIpv6EndpointsWithBrackets() {
        assertThat(Endpoint.of("10.0.0.1", 8583)).hasToString("10.0.0.1:8583");
        assertThat(Endpoint.of("2001:db8::1", 443)).hasToString("[2001:db8::1]:443");
    }

    @Test
    void parsesWhatItWrites() {
        for (String text : new String[] {"10.0.0.1:8583", "[2001:db8::1]:443", "[::]:0"}) {
            assertThat(Endpoint.parse(text)).hasToString(text);
        }
        assertThatThrownBy(() -> Endpoint.parse("::1:80")).isInstanceOf(InvalidAddressException.class);
        assertThatThrownBy(() -> Endpoint.parse("10.0.0.1")).isInstanceOf(InvalidAddressException.class);
    }
}
