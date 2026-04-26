package net.coderbot.iris.shaderpack.materialmap;

import net.coderbot.iris.Iris;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        final StringBuilder blockIdBuilder = new StringBuilder();
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
                appendCurrent(inBracket, readingValue, blockIdBuilder, nbtKey, nbtValue, c);
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
                    appendCurrent(inBracket, readingValue, blockIdBuilder, nbtKey, nbtValue, c);
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
                    blockIdBuilder.append(c);
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

        final String baseEntry = blockIdBuilder.toString().trim();
        final Map<String, NbtValue> nbtProperties = nbt.isEmpty() ? Collections.emptyMap() : nbt;

        return splitBaseEntry(entry, baseEntry, nbtProperties);
    }

    /**
     * Splits the bracket-stripped portion of a block identifier (e.g. {@code minecraft:furnace:lit=true})
     * into its components.
     *
     * Accepted forms:
     *   stone
     *   stone:0
     *   minecraft:stone
     *   minecraft:stone:0
     *   minecraft:stone:0,1,2
     *   minecraft:furnace:lit=true               (blockstate property)
     *   minecraft:oak_log:axis=y,x:variant=oak     (multiple blockstate properties)
     *   flower_pot:1[Item=minecraft:red_flower,Data=0]  (used together with NBT brackets)
     */
    private static ParsedBlockIdentifier splitBaseEntry(String entry, String baseEntry, Map<String, NbtValue> nbtProperties) {
        final Set<Integer> metas = new HashSet<>();
        final Map<String, String> stateProperties = new LinkedHashMap<>();

        if (baseEntry.isEmpty()) {
            return new ParsedBlockIdentifier(
                new NamespacedId("minecraft", baseEntry),
                Collections.emptySet(),
                Collections.emptyMap(),
                nbtProperties
            );
        }

        final String[] splitStates = baseEntry.split(":");

        // Trivial: "stone"
        if (splitStates.length == 1) {
            return new ParsedBlockIdentifier(
                new NamespacedId("minecraft", baseEntry),
                Collections.emptySet(),
                Collections.emptyMap(),
                nbtProperties
            );
        }

        // Two-segment with no metas/state properties: "minecraft:stone"
        if (splitStates.length == 2
                && !StringUtils.isNumeric(splitStates[1].substring(0, 1))
                && !splitStates[1].contains("=")) {
            return new ParsedBlockIdentifier(
                new NamespacedId(splitStates[0], splitStates[1]),
                Collections.emptySet(),
                Collections.emptyMap(),
                nbtProperties
            );
        }

        // Metas and/or state properties
        final NamespacedId id;
        final int statesStart;

        if (splitStates.length == 2) {
            // "stone:0" or "stone:lit=true"
            id = new NamespacedId("minecraft", splitStates[0]);
            statesStart = 1;

        } else if (StringUtils.isNumeric(splitStates[1].substring(0, 1)) || splitStates[1].contains("=")) {
            // "stone:0:something" or "stone:lit=true:something" — unlikely but handle it
            id = new NamespacedId("minecraft", splitStates[0]);
            statesStart = 1;

        } else {
            // "minecraft:stone:0" or "minecraft:furnace:lit=true"
            id = new NamespacedId(splitStates[0], splitStates[1]);
            statesStart = 2;
        }

        for (int index = statesStart; index < splitStates.length; index++) {
            final String segment = splitStates[index];

            if (segment.contains("=")) {
                // Blockstate property segment: key=value[,key=value...]
                for (String prop : segment.split(",")) {
                    final int eq = prop.indexOf('=');
                    if (eq > 0 && eq < prop.length() - 1) {
                        stateProperties.put(prop.substring(0, eq), prop.substring(eq + 1));
                    }
                }
            } else {
                // Metadata segment: comma-separated integers
                for (String metaPart : segment.split(",")) {
                    try {
                        metas.add(Integer.parseInt(metaPart));
                    } catch (NumberFormatException e) {
                        Iris.logger.warn("Warning: the block ID map entry \"{}\" could not be fully parsed:", entry);
                        Iris.logger.warn("- Metadata ids must be a comma separated list of one or more integers, but {} is not of that form!", segment);
                    }
                }
            }
        }

        return new ParsedBlockIdentifier(
            id,
            metas.isEmpty() ? Collections.emptySet() : metas,
            stateProperties.isEmpty() ? Collections.emptyMap() : stateProperties,
            nbtProperties
        );
    }

    private static void appendCurrent(boolean inBracket, boolean readingValue,
                                      StringBuilder blockIdBuilder, StringBuilder nbtKey,
                                      StringBuilder nbtValue, char c) {
        if (!inBracket) {
            blockIdBuilder.append(c);

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

    public record ParsedBlockIdentifier(
        NamespacedId id,
        Set<Integer> metas,
        Map<String, String> stateProperties,
        Map<String, NbtValue> nbtProperties
    ) {
    }
}
