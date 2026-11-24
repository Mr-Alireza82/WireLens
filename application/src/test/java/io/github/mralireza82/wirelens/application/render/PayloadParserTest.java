package io.github.mralireza82.wirelens.application.render;

import io.github.mralireza82.wirelens.domain.traffic.Payload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayloadParserTest {
    private final PayloadParser parser = new PayloadParser();
    private final PayloadRenderer renderer = new PayloadRenderer();

    @ParameterizedTest(name = "{1}: {0}")
    @CsvSource(delimiter = '|', value = {
            "008E           | HEXADECIMAL",
            "00 8e          | HEXADECIMAL",
            "00-8E          | HEXADECIMAL",
            "0x00, 0x8E     | HEXADECIMAL",
            "0 142          | DECIMAL",
            "000,142        | DECIMAL",
            "0 216          | OCTAL",
            "0o0 0o216      | OCTAL",
            "0000000010001110 | BINARY",
            "0b0 0b10001110 | BINARY",
    })
    void readsNumbersInEveryNotation(String input, Radix radix) {
        assertThat(parser.parseNumbers(input, radix)).containsExactly(0x00, 0x8E);
    }

    @Test
    void roundTripsEveryRenderingBackToTheSameBytes() {
        Random random = new Random(7);
        for (int round = 0; round < 200; round++) {
            byte[] bytes = new byte[1 + random.nextInt(64)];
            random.nextBytes(bytes);
            for (Radix radix : Radix.values()) {
                for (String separator : new String[] {" ", "-", ":", ", "}) {
                    String text = renderer.numbers(Payload.copyOf(bytes),
                            new RenderSettings(TextEncoding.ASCII, radix, separator, round % 3 == 0 ? 8 : 0));
                    assertThat(parser.parseNumbers(text, radix)).as("%s %s", radix, text).isEqualTo(bytes);
                }
            }
            String raw = renderer.rawHex(Payload.copyOf(bytes));
            assertThat(parser.parseNumbers(raw, Radix.HEXADECIMAL)).isEqualTo(bytes);
        }
    }

    @Test
    void explainsBadNumbers() {
        assertThatThrownBy(() -> parser.parseNumbers("12 256", Radix.DECIMAL))
                .isInstanceOf(PayloadSyntaxException.class).hasMessageContaining("Value 2 ('256') is larger than one byte");
        assertThatThrownBy(() -> parser.parseNumbers("ABC", Radix.HEXADECIMAL))
                .hasMessageContaining("2 digits per byte");
        assertThatThrownBy(() -> parser.parseNumbers("12 G1", Radix.HEXADECIMAL))
                .hasMessageContaining("'G' is not a hexadecimal digit");
        assertThatThrownBy(() -> parser.parseNumbers("  ", Radix.HEXADECIMAL)).hasMessage("Nothing to send");
        assertThatThrownBy(() -> parser.parseNumbers("0x", Radix.HEXADECIMAL)).hasMessageContaining("no digits");
    }

    @Test
    void encodesTextWithEscapes() {
        assertThat(parser.parseText("\\x02PING\\x03\\r\\n", TextEncoding.ASCII))
                .containsExactly(0x02, 'P', 'I', 'N', 'G', 0x03, '\r', '\n');
        assertThat(parser.parseText("a\\\\b\\t\\0", TextEncoding.ASCII)).containsExactly('a', '\\', 'b', '\t', 0);
        assertThat(parser.parseText("A\\xFF", TextEncoding.EBCDIC_037)).containsExactly(0xC1, 0xFF);
    }

    @Test
    void rejectsBadEscapes() {
        assertThatThrownBy(() -> parser.parseText("abc\\", TextEncoding.ASCII)).isInstanceOf(PayloadSyntaxException.class);
        assertThatThrownBy(() -> parser.parseText("\\q", TextEncoding.ASCII)).hasMessageContaining("Unknown escape");
        assertThatThrownBy(() -> parser.parseText("\\x2", TextEncoding.ASCII)).hasMessageContaining("two hex digits");
        assertThatThrownBy(() -> parser.parseText("\\xZZ", TextEncoding.ASCII)).hasMessageContaining("not a hex byte");
        assertThatThrownBy(() -> parser.parseText("", TextEncoding.ASCII)).hasMessage("Nothing to send");
    }
}
