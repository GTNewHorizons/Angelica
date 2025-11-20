package com.gtnewhorizons.angelica.client.font.color;

import java.util.Arrays;

/**
 * Holds the text and formatting metadata that a {@link AngelicaColorResolver}
 * produced for a draw call.
 */
public final class ResolvedText {

    private static final byte FLAG_BOLD = 1;
    private static final byte FLAG_STRIKETHROUGH = 1 << 1;
    private static final byte FLAG_UNDERLINE = 1 << 2;
    private static final byte FLAG_ITALIC = 1 << 3;
    private static final byte FLAG_RANDOM = 1 << 4;

    static final byte EFFECT_RAINBOW = 1;
    static final byte EFFECT_DINNERBONE = 1 << 1;
    static final byte EFFECT_IGNITE = 1 << 2;
    static final byte EFFECT_SHAKE = 1 << 3;

    private final char[] characters;
    private final int[] colors;
    private final int[] shadowColors;
    private final byte[] flags;
    private final byte[] effectFlags;
    private final int[] effectBaseColors;
    private final int[] effectParameters;

    private ResolvedText(char[] characters, int[] colors, int[] shadowColors, byte[] flags, byte[] effectFlags,
                         int[] effectBaseColors, int[] effectParameters) {
        this.characters = characters;
        this.colors = colors;
        this.shadowColors = shadowColors;
        this.flags = flags;
        this.effectFlags = effectFlags;
        this.effectBaseColors = effectBaseColors;
        this.effectParameters = effectParameters;
    }

    public int length() {
        return characters.length;
    }

    public char charAt(int index) {
        return characters[index];
    }

    public int colorAt(int index) {
        return colors[index];
    }

    public int shadowColorAt(int index) {
        return shadowColors[index];
    }

    public boolean isBold(int index) {
        return (flags[index] & FLAG_BOLD) != 0;
    }

    public boolean isStrikethrough(int index) {
        return (flags[index] & FLAG_STRIKETHROUGH) != 0;
    }

    public boolean isUnderline(int index) {
        return (flags[index] & FLAG_UNDERLINE) != 0;
    }

    public boolean isItalic(int index) {
        return (flags[index] & FLAG_ITALIC) != 0;
    }

    public boolean isRandom(int index) {
        return (flags[index] & FLAG_RANDOM) != 0;
    }

    public boolean hasDynamicEffects() {
        return effectFlags.length > 0;
    }

    public boolean isRainbow(int index) {
        return (effectFlags[index] & EFFECT_RAINBOW) != 0;
    }

    public boolean isDinnerbone(int index) {
        return (effectFlags[index] & EFFECT_DINNERBONE) != 0;
    }

    public boolean isIgnite(int index) {
        return (effectFlags[index] & EFFECT_IGNITE) != 0;
    }

    public boolean isShake(int index) {
        return (effectFlags[index] & EFFECT_SHAKE) != 0;
    }

    public int effectBaseColorAt(int index) {
        return effectBaseColors[index];
    }

    public int effectParameterAt(int index) {
        return effectParameters[index];
    }

    /**
     * Returns the resolved characters as a {@link String}. This is primarily
     * used by compatibility layers that need random access to the textual
     * content after Angelica has applied HexText sanitization rules.
     */
    public String asString() {
        return new String(characters);
    }

    public static Builder builder(int initialCapacity) {
        return new Builder(initialCapacity);
    }

    public static final class Builder {

        private char[] characters;
        private int[] colors;
        private int[] shadowColors;
        private byte[] flags;
        private byte[] effectFlags;
        private int[] effectBaseColors;
        private int[] effectParameters;
        private int size;

        public Builder(int initialCapacity) {
            int cap = Math.max(16, initialCapacity);
            this.characters = new char[cap];
            this.colors = new int[cap];
            this.shadowColors = new int[cap];
            this.flags = new byte[cap];
            this.effectFlags = new byte[cap];
            this.effectBaseColors = new int[cap];
            this.effectParameters = new int[cap];
        }

        public void append(char character, int color, int shadowColor, boolean bold, boolean italic,
                            boolean underline, boolean strikethrough, boolean random) {
            append(character, color, shadowColor, bold, italic, underline, strikethrough, random, (byte) 0, 0, 0);
        }

        public void append(char character, int color, int shadowColor, boolean bold, boolean italic,
                            boolean underline, boolean strikethrough, boolean random, byte dynFlags,
                            int effectParameter, int effectBaseColor) {
            ensureCapacity(size + 1);
            characters[size] = character;
            colors[size] = color;
            shadowColors[size] = shadowColor;
            byte flagBits = 0;
            if (bold) {
                flagBits |= FLAG_BOLD;
            }
            if (strikethrough) {
                flagBits |= FLAG_STRIKETHROUGH;
            }
            if (underline) {
                flagBits |= FLAG_UNDERLINE;
            }
            if (italic) {
                flagBits |= FLAG_ITALIC;
            }
            if (random) {
                flagBits |= FLAG_RANDOM;
            }
            flags[size] = flagBits;
            effectFlags[size] = dynFlags;
            effectParameters[size] = effectParameter;
            effectBaseColors[size] = effectBaseColor;
            size++;
        }

        private void ensureCapacity(int minCapacity) {
            if (minCapacity <= characters.length) {
                return;
            }
            int newCap = characters.length * 2;
            while (newCap < minCapacity) {
                newCap *= 2;
            }
            characters = Arrays.copyOf(characters, newCap);
            colors = Arrays.copyOf(colors, newCap);
            shadowColors = Arrays.copyOf(shadowColors, newCap);
            flags = Arrays.copyOf(flags, newCap);
            effectFlags = Arrays.copyOf(effectFlags, newCap);
            effectBaseColors = Arrays.copyOf(effectBaseColors, newCap);
            effectParameters = Arrays.copyOf(effectParameters, newCap);
        }

        public ResolvedText build() {
            return new ResolvedText(
                Arrays.copyOf(characters, size),
                Arrays.copyOf(colors, size),
                Arrays.copyOf(shadowColors, size),
                Arrays.copyOf(flags, size),
                Arrays.copyOf(effectFlags, size),
                Arrays.copyOf(effectBaseColors, size),
                Arrays.copyOf(effectParameters, size)
            );
        }
    }
}
