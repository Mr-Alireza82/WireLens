package io.github.mralireza82.wirelens.adapter.storage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Just enough JSON for flat capture records (strings, integers, booleans, null). Keeps the storage adapter free of
 * third-party dependencies; a full JSON library would be overkill for one object per line.
 */
final class Json {
    private Json() {
    }

    /** Builds one JSON object, preserving insertion order. */
    static final class ObjectWriter {
        private final StringBuilder text = new StringBuilder(128).append('{');

        ObjectWriter put(String key, String value) {
            if (value != null) {
                key(key);
                quote(value);
            }
            return this;
        }

        ObjectWriter put(String key, long value) {
            key(key);
            text.append(value);
            return this;
        }

        private void key(String key) {
            if (text.length() > 1) {
                text.append(',');
            }
            quote(key);
            text.append(':');
        }

        private void quote(String value) {
            text.append('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> text.append("\\\"");
                    case '\\' -> text.append("\\\\");
                    case '\n' -> text.append("\\n");
                    case '\r' -> text.append("\\r");
                    case '\t' -> text.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            text.append(String.format("\\u%04x", (int) c));
                        } else {
                            text.append(c);
                        }
                    }
                }
            }
            text.append('"');
        }

        String build() {
            return text.append('}').toString();
        }
    }

    /** Parses one flat JSON object into strings, longs, booleans and nulls. */
    static Map<String, Object> parseObject(String line) {
        Parser parser = new Parser(line);
        Map<String, Object> result = parser.object();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("unexpected text after the object");
        }
        return result;
    }

    private static final class Parser {
        private final String text;
        private int position;

        Parser(String text) {
            this.text = text;
        }

        Map<String, Object> object() {
            skipWhitespace();
            expect('{');
            Map<String, Object> values = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                position++;
                return values;
            }
            while (true) {
                skipWhitespace();
                String key = string();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                values.put(key, value());
                skipWhitespace();
                char next = next();
                if (next == '}') {
                    return values;
                }
                if (next != ',') {
                    throw error("expected ',' or '}'");
                }
            }
        }

        private Object value() {
            char c = peek();
            if (c == '"') {
                return string();
            }
            if (c == '-' || Character.isDigit(c)) {
                int start = position;
                position++;
                while (!atEnd() && Character.isDigit(peek())) {
                    position++;
                }
                return Long.parseLong(text.substring(start, position));
            }
            for (String literal : new String[] {"true", "false", "null"}) {
                if (text.startsWith(literal, position)) {
                    position += literal.length();
                    return literal.equals("null") ? null : Boolean.valueOf(literal);
                }
            }
            throw error("unsupported value");
        }

        private String string() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return value.toString();
                }
                if (c != '\\') {
                    value.append(c);
                    continue;
                }
                char escaped = next();
                switch (escaped) {
                    case '"', '\\', '/' -> value.append(escaped);
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'u' -> {
                        if (position + 4 > text.length()) {
                            throw error("incomplete \\u escape");
                        }
                        value.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                        position += 4;
                    }
                    default -> throw error("unknown escape \\" + escaped);
                }
            }
        }

        void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(peek())) {
                position++;
            }
        }

        boolean atEnd() {
            return position >= text.length();
        }

        private char peek() {
            if (atEnd()) {
                throw error("unexpected end of line");
            }
            return text.charAt(position);
        }

        private char next() {
            char c = peek();
            position++;
            return c;
        }

        private void expect(char expected) {
            if (next() != expected) {
                throw error("expected '" + expected + "'");
            }
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at column " + (position + 1));
        }
    }
}
