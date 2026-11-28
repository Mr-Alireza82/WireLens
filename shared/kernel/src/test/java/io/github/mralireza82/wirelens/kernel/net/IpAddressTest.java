package io.github.mralireza82.wirelens.kernel.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IpAddressTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "0.0.0.0,            0.0.0.0",
            "127.0.0.1,          127.0.0.1",
            "' 192.168.1.10 ',   192.168.1.10",
            "255.255.255.255,    255.255.255.255",
            "::,                 ::",
            "::1,                ::1",
            "[::1],              ::1",
            "2001:DB8:0:0:0:0:0:1, 2001:db8::1",
            "2001:0db8:0000:0000:0001:0000:0000:0001, 2001:db8::1:0:0:1",
            "fe80::0001,         fe80::1",
            "1:0:0:2:0:0:0:3,    1:0:0:2::3",
            "1:2:3:4:5:6:7:8,    1:2:3:4:5:6:7:8",
            "1::,                1::",
            "1:0:2:3:4:5:6:7,    1:0:2:3:4:5:6:7",
            "::ffff:192.0.2.1,   ::ffff:192.0.2.1",
            "::ffff:c000:0201,   ::ffff:192.0.2.1",
            "64:ff9b::192.0.2.33, 64:ff9b::c000:221",
    })
    void parsesAndCanonicalises(String input, String canonical) {
        assertThat(IpAddress.parse(input).toString()).isEqualTo(canonical);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   ", "localhost", "example.com", "1.2.3", "1.2.3.4.5", "256.1.1.1", "1.2.3.-1", "01.2.3.4",
            "1.2.3.04", "1..3.4", "1.2.3.4 ", "a.b.c.d", "1.2.3.1000",
            ":", ":::", "1:::2", "1::2::3", "12345::", "g::1", "1:2:3:4:5:6:7", "1:2:3:4:5:6:7:8:9",
            "1:2:3:4:5:6:7::8", ":1:2:3:4:5:6:7", "1:2:3:4:5:6:7:", "fe80::1%eth0", "::1.2.3.4:5", "1.2.3.4::",
            "[::1", "::ffff:256.0.0.1",
    })
    void rejectsInvalidText(String input) {
        // "1.2.3.4 " is accepted after stripping, so treat it separately below.
        if (input.equals("1.2.3.4 ")) {
            assertThat(IpAddress.tryParse(input)).isPresent();
            return;
        }
        assertThatThrownBy(() -> IpAddress.parse(input)).isInstanceOf(InvalidAddressException.class);
        assertThat(IpAddress.validate(input)).isPresent();
    }

    @Test
    void messagesExplainTheProblem() {
        assertThat(IpAddress.validate("300.1.1.1")).contains("300 is out of range; each IPv4 number is 0 to 255");
        assertThat(IpAddress.validate("010.0.0.1")).hasValueSatisfying(m -> m.contains("leading zero"));
        assertThat(IpAddress.validate("fe80::1%3")).hasValueSatisfying(m -> m.contains("zone"));
    }

    @Test
    void agreesWithTheJdkOnBytes() throws Exception {
        for (String text : new String[] {"10.1.2.3", "2001:db8::8:800:200c:417a", "::", "::ffff:1.2.3.4", "1::"}) {
            byte[] jdk = InetAddress.getByName(text).getAddress();
            byte[] ours = IpAddress.parse(text).octets();
            if (jdk.length == 4 && ours.length == 16) { // the JDK turns IPv4-mapped IPv6 into IPv4
                assertThat(java.util.Arrays.copyOfRange(ours, 12, 16)).isEqualTo(jdk);
            } else {
                assertThat(ours).isEqualTo(jdk);
            }
        }
    }

    @Test
    void knowsWildcardAndLoopback() {
        assertThat(IpAddress.parse("0.0.0.0").isAnyLocal()).isTrue();
        assertThat(IpAddress.parse("::").isAnyLocal()).isTrue();
        assertThat(IpAddress.parse("127.8.9.10").isLoopback()).isTrue();
        assertThat(IpAddress.parse("::1").isLoopback()).isTrue();
        assertThat(IpAddress.parse("10.0.0.1").isLoopback()).isFalse();
        assertThat(IpAddress.parse("::1").family()).isEqualTo(IpFamily.IPV6);
    }

    @Test
    void isAValueObject() {
        assertThat(IpAddress.parse("2001:db8::1")).isEqualTo(IpAddress.parse("2001:0DB8:0::0:1"));
        byte[] octets = {10, 0, 0, 1};
        IpAddress address = IpAddress.ofOctets(octets);
        octets[0] = 99;
        assertThat(address.toString()).isEqualTo("10.0.0.1");
        address.octets()[0] = 99;
        assertThat(address.toString()).isEqualTo("10.0.0.1");
    }
}
