package io.github.mralireza82.wirelens.application.render;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Character encodings bytes can be decoded with (Strategy). Characters that cannot be shown (control codes,
 * malformed sequences) become {@link #UNPRINTABLE}, so decoded text is always safe to display and copy, while the
 * numeric view still shows every byte exactly.
 */
public enum TextEncoding {
    /** 7-bit US-ASCII. Bytes 0x80..0xFF are not ASCII and show as unprintable. */
    ASCII("ASCII", "US-ASCII", true),
    UTF_8("UTF-8", "UTF-8", false),
    /** Latin-1: every byte is one character. */
    ISO_8859_1("ISO-8859-1", "ISO-8859-1", true),
    /** EBCDIC code page 037, still common on payment hosts and mainframes. */
    EBCDIC_037("EBCDIC (IBM037)", "IBM037", true),
    UTF_16BE("UTF-16BE", "UTF-16BE", false);

    /** Shown instead of a byte or character that has no visible form. */
    public static final char UNPRINTABLE = '·';

    private final String displayName;
    private final String charsetName;
    private final boolean singleByte;
    private volatile char[] byteTable;

    TextEncoding(String displayName, String charsetName, boolean singleByte) {
        this.displayName = displayName;
        this.charsetName = charsetName;
        this.singleByte = singleByte;
    }

    public String displayName() {
        return displayName;
    }

    /** The JDK charset, or empty if this runtime does not ship it (IBM037 lives in {@code jdk.charsets}). */
    public Optional<Charset> charset() {
        try {
            return Optional.of(Charset.forName(charsetName));
        } catch (UnsupportedCharsetException e) {
            return Optional.empty();
        }
    }

    public boolean isAvailable() {
        return charset().isPresent();
    }

    /** Encodings this runtime can decode, in menu order. */
    public static List<TextEncoding> available() {
        return Arrays.stream(values()).filter(TextEncoding::isAvailable).toList();
    }

    /** True when every byte maps to exactly one character (enables per-byte views such as the hex dump). */
    public boolean isSingleByte() {
        return singleByte;
    }

    /** Decodes {@code length} bytes, replacing anything unprintable with {@link #UNPRINTABLE}. */
    public String decode(byte[] bytes, int offset, int length) {
        if (singleByte) {
            char[] table = byteTable();
            char[] text = new char[length];
            for (int i = 0; i < length; i++) {
                text[i] = table[bytes[offset + i] & 0xFF];
            }
            return new String(text);
        }
        Charset charset = charset().orElse(StandardCharsets.UTF_8);
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .replaceWith(String.valueOf(UNPRINTABLE));
        try {
            CharBuffer chars = decoder.decode(ByteBuffer.wrap(bytes, offset, length));
            StringBuilder text = new StringBuilder(chars.length());
            while (chars.hasRemaining()) {
                char c = chars.get();
                text.append(isPrintable(c) || Character.isSurrogate(c) ? c : UNPRINTABLE);
            }
            return text.toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            throw new IllegalStateException("decoder with REPLACE actions cannot fail", e);
        }
    }

    public String decode(byte[] bytes) {
        return decode(bytes, 0, bytes.length);
    }

    /**
     * The character for one byte, used by per-byte views. Multi-byte encodings fall back to ASCII here, since a
     * single byte has no meaning on its own in UTF-8 or UTF-16.
     */
    public char charForByte(int unsignedByte) {
        return (singleByte ? byteTable() : ASCII.byteTable())[unsignedByte];
    }

    /** Encodes text typed by the user. */
    public byte[] encode(String text) {
        return text.getBytes(charset().orElseThrow(() ->
                new IllegalStateException(displayName + " is not available in this Java runtime")));
    }

    private char[] byteTable() {
        char[] table = byteTable;
        if (table == null) {
            table = new char[256];
            Charset charset = charset().orElse(StandardCharsets.ISO_8859_1);
            for (int value = 0; value < 256; value++) {
                String decoded = new String(new byte[] {(byte) value}, charset);
                char c = decoded.length() == 1 ? decoded.charAt(0) : UNPRINTABLE;
                boolean valid = c != '�' && isPrintable(c) && (this != ASCII || value < 0x80);
                table[value] = valid ? c : UNPRINTABLE;
            }
            byteTable = table;
        }
        return table;
    }

    private static boolean isPrintable(char c) {
        if (Character.isISOControl(c) || c == '�') {
            return false;
        }
        int type = Character.getType(c);
        return type != Character.UNASSIGNED && type != Character.FORMAT && type != Character.LINE_SEPARATOR
                && type != Character.PARAGRAPH_SEPARATOR && type != Character.PRIVATE_USE;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
