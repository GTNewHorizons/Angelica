package net.coderbot.iris.shaderpack.materialmap;

import net.coderbot.iris.Iris;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// This got complicated fast

/**
 * This class handles all processing
 */
public final class PropertiesTokenizer {

    private PropertiesTokenizer() {
    }

    /**
     * Splits a property value into individual block identifier tokens.
     */
    public static List<String> tokenizeValues(String input, char delimiter) {
        if (input.indexOf('"') == -1 && input.indexOf('\\') == -1 && input.indexOf('[') == -1) {
            return tokenizeSimple(input, delimiter);
        }
        return tokenizeFull(input, delimiter);
    }

    // Most blocks should take this path
    private static List<String> tokenizeSimple(String input, char delimiter) {
        final List<String> result = new ArrayList<>();
        final boolean isWhitespaceDelim = Character.isWhitespace(delimiter);

        if (isWhitespaceDelim) {
            for (String part : input.split("\\s+")) {
                if (!part.isEmpty()) {
                    result.add(part);
                }
            }
        } else {
            for (String part : input.split(String.valueOf(delimiter))) {
                final String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    // Found special characters
    private static List<String> tokenizeFull(String input, char delimiter) {
        final boolean isWhitespaceDelim = Character.isWhitespace(delimiter);
        final List<String> result = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;
        int bracketDepth = 0;

        for (int i = 0; i < input.length(); i++) {
            final char c = input.charAt(i);

            if (escaped) {
                if (bracketDepth > 0) {
                    // Preserve escapes inside brackets for parseBlockIdentifier
                    current.append('\\');
                }
                current.append(c);
                escaped = false;

            } else if (c == '\\') {
                escaped = true;

            } else if (bracketDepth > 0) {
                current.append(c);

                if (c == '[') {
                    bracketDepth++;
                }

                else if (c == ']') {
                    bracketDepth--;
                }

            } else if (c == '[' && !inQuotes) {
                current.append(c);
                bracketDepth = 1;

            } else if (c == '"') {
                inQuotes = !inQuotes;

            } else if (!inQuotes && (isWhitespaceDelim ? Character.isWhitespace(c) : c == delimiter)) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }

            } else {
                current.append(c);
            }
        }

        if (inQuotes) {
            Iris.logger.warn("Unclosed quote in properties token: {}", input);
        }

        if (!current.isEmpty()) {
            result.add(current.toString());
        }

        return result;
    }

    // Begin reading the special case block
    public static ParsedBlockIdentifier parseBlockIdentifier(String entry) {
        final StringBuilder blockId = new StringBuilder();
        final Map<String, NbtValue> nbt = new LinkedHashMap<>();

        boolean escaped = false;
        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;
        boolean inBracket = false;

        // NBT pair
        StringBuilder nbtKey = new StringBuilder();
        StringBuilder nbtValue = new StringBuilder();
        boolean readingValue = false;
        boolean valueLiteral = false;

        for (int i = 0; i < entry.length(); i++) {
            final char c = entry.charAt(i);

            // Ignore special characters
            if (escaped) {
                appendCurrent(inBracket, readingValue, blockId, nbtKey, nbtValue, c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }

            // Detect special characters
            if (inSingleQuotes) {
                if (c == '\'') {
                    inSingleQuotes = false;

                } else {
                    nbtValue.append(c);
                }
                continue;
            }

            if (inDoubleQuotes) {
                if (c == '"') {
                    inDoubleQuotes = false;

                } else {
                    appendCurrent(inBracket, readingValue, blockId, nbtKey, nbtValue, c);
                }
                continue;
            }


            if (c == '"') {
                inDoubleQuotes = true;
                continue;
            }

            if (!inBracket) {
                if (c == '[') {
                    inBracket = true;
                    nbtKey.setLength(0);
                    nbtValue.setLength(0);
                    readingValue = false;
                    valueLiteral = false;

                } else {
                    blockId.append(c);
                }

            } else {
                if (c == ']') {
                    flushNbtPair(nbtKey, nbtValue, readingValue, valueLiteral, nbt);
                    inBracket = false;

                } else if (c == '=' && !readingValue) {
                    readingValue = true;
                    valueLiteral = false;

                } else if (c == '\'' && readingValue && nbtValue.isEmpty()) {
                    inSingleQuotes = true;
                    valueLiteral = true;

                } else if (c == ',') {
                    flushNbtPair(nbtKey, nbtValue, readingValue, valueLiteral, nbt);
                    nbtKey.setLength(0);
                    nbtValue.setLength(0);
                    readingValue = false;
                    valueLiteral = false;

                } else {
                    if (readingValue) {
                        nbtValue.append(c);

                    } else {
                        nbtKey.append(c);
                    }
                }
            }
        }

        if (inDoubleQuotes) {
            Iris.logger.warn("Unclosed double quote in block entry: {}", entry);
        }
        if (inSingleQuotes) {
            Iris.logger.warn("Unclosed single quote in block entry: {}", entry);
        }
        if (escaped) {
            Iris.logger.warn("Trailing backslash in block entry: {}", entry);
        }
        if (inBracket) {
            Iris.logger.warn("Unclosed bracket in block entry: {}", entry);
            flushNbtPair(nbtKey, nbtValue, readingValue, valueLiteral, nbt);
        }

        return new ParsedBlockIdentifier(
            blockId.toString().trim(),
            nbt.isEmpty() ? Collections.emptyMap() : nbt
        );
    }

    private static void appendCurrent(boolean inBracket, boolean readingValue,
                                      StringBuilder blockId, StringBuilder nbtKey,
                                      StringBuilder nbtValue, char c) {
        if (!inBracket) {
            blockId.append(c);

        } else if (readingValue) {
            nbtValue.append(c);

        } else {
            nbtKey.append(c);
        }
    }

    private static void flushNbtPair(StringBuilder key, StringBuilder value,
                                     boolean readingValue, boolean valueLiteral,
                                     Map<String, NbtValue> nbt) {
        final String k = key.toString().trim();
        if (k.isEmpty()) return;

        if (!readingValue) {
            // No = was found, existence is good enough
            nbt.put(k, null);
            return;
        }

        nbt.put(k, new NbtValue(value.toString().trim(), valueLiteral));
    }

    public static String stripQuotes(String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    public record NbtValue(String value, boolean literal) {
    }

    public record ParsedBlockIdentifier(String blockId, Map<String, NbtValue> nbtProperties) {
    }
}
