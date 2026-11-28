package io.github.mralireza82.wirelens.inspection.application.render;

import io.github.mralireza82.wirelens.kernel.bytes.Payload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadRendererTest {
    private final PayloadRenderer renderer = new PayloadRenderer();
    private final Payload twoBytes = Payload.copyOf(new byte[] {0x00, (byte) 0x8E});

    @ParameterizedTest(name = "{0} with ''{1}'' -> {2}")
    @CsvSource(delimiter = '|', value = {
            "HEXADECIMAL | ''  | 008E",
            "HEXADECIMAL | ' ' | 00 8E",
            "HEXADECIMAL | '-' | 00-8E",
            "HEXADECIMAL | ', 0x' | 00, 0x8E",
            "DECIMAL     | ' ' | 000 142",
            "OCTAL       | ' ' | 000 216",
            "BINARY      | ' ' | 00000000 10001110",
    })
    void writesEveryByteWithFixedWidthAndTheChosenSeparator(Radix radix, String separator, String expected) {
        RenderSettings settings = new RenderSettings(TextEncoding.ASCII, radix, separator, 0);
        assertThat(renderer.numbers(twoBytes, settings)).isEqualTo(expected);
    }

    @Test
    void agreesWithTheJdkForEveryByteAndBase() {
        byte[] all = new byte[256];
        for (int i = 0; i < 256; i++) {
            all[i] = (byte) i;
        }
        for (Radix radix : Radix.values()) {
            String[] digits = renderer.numbers(Payload.copyOf(all), new RenderSettings(TextEncoding.ASCII, radix, " ", 0))
                    .split(" ");
            assertThat(digits).hasSize(256);
            for (int i = 0; i < 256; i++) {
                assertThat(Integer.parseInt(digits[i], radix.radix())).isEqualTo(i);
                assertThat(digits[i]).hasSize(radix.digitsPerByte());
            }
        }
    }

    @Test
    void breaksLinesAndTruncates() {
        Payload payload = Payload.copyOf(new byte[] {1, 2, 3, 4, 5});
        RenderSettings settings = new RenderSettings(TextEncoding.ASCII, Radix.HEXADECIMAL, " ", 2);
        assertThat(renderer.numbers(payload, settings)).isEqualTo("01 02\n03 04\n05");
        assertThat(renderer.numbers(payload, settings, 3)).isEqualTo("01 02\n03");
        assertThat(renderer.numbers(Payload.empty(), settings)).isEmpty();
    }

    @Test
    void decodesTextAndHidesUnprintableBytes() {
        Payload payload = Payload.copyOf("Hi\r\n\u0002".getBytes(StandardCharsets.US_ASCII));
        assertThat(renderer.text(payload, TextEncoding.ASCII)).isEqualTo("Hi···");
        assertThat(renderer.text(Payload.copyOf(new byte[] {(byte) 0xE9}), TextEncoding.ASCII)).isEqualTo("·");
        assertThat(renderer.text(Payload.copyOf(new byte[] {(byte) 0xE9}), TextEncoding.ISO_8859_1)).isEqualTo("é");
        assertThat(renderer.text(Payload.copyOf("çé€".getBytes(StandardCharsets.UTF_8)), TextEncoding.UTF_8))
                .isEqualTo("çé€");
        assertThat(renderer.text(Payload.copyOf(new byte[] {(byte) 0xC3}), TextEncoding.UTF_8)).isEqualTo("·");
    }

    @Test
    void decodesEbcdic() {
        // "ABC 123" in EBCDIC 037
        byte[] ebcdic = {(byte) 0xC1, (byte) 0xC2, (byte) 0xC3, 0x40, (byte) 0xF1, (byte) 0xF2, (byte) 0xF3};
        assertThat(TextEncoding.EBCDIC_037.isAvailable()).isTrue();
        assertThat(renderer.text(Payload.copyOf(ebcdic), TextEncoding.EBCDIC_037)).isEqualTo("ABC 123");
        assertThat(TextEncoding.EBCDIC_037.encode("ABC 123")).isEqualTo(ebcdic);
    }

    @Test
    void textCanBeCutToAPrefix() {
        assertThat(renderer.text(Payload.copyOf("abcdef".getBytes(StandardCharsets.US_ASCII)), TextEncoding.ASCII, 3))
                .isEqualTo("abc");
    }

    @Test
    void writesCanonicalRawHex() {
        assertThat(renderer.rawHex(twoBytes)).isEqualTo("008E");
    }

    @Test
    void dumpsLikeHexdump() {
        Payload payload = Payload.copyOf("Hello, WireLens!\r\n".getBytes(StandardCharsets.US_ASCII));
        assertThat(renderer.hexDump(payload, TextEncoding.ASCII)).isEqualTo(
                "00000000  48 65 6C 6C 6F 2C 20 57  69 72 65 4C 65 6E 73 21  |Hello, WireLens!|\n"
                        + "00000010  0D 0A                                             |··|\n");
    }

    @Test
    void settingsAreImmutableValues() {
        RenderSettings changed = RenderSettings.DEFAULT.withRadix(Radix.BINARY).withSeparator("-");
        assertThat(RenderSettings.DEFAULT.radix()).isEqualTo(Radix.HEXADECIMAL);
        assertThat(changed.summary()).isEqualTo("ASCII · BIN");
    }
}
