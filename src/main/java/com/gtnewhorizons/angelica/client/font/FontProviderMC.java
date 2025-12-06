package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.FontConfig;
import it.unimi.dsi.fastutil.chars.Char2ShortOpenHashMap;
import jss.util.RandomXoshiro256StarStar;
import net.minecraft.util.ResourceLocation;

public final class FontProviderMC implements FontProvider {

    /**
     * The full list of characters present in the default Minecraft font, excluding the Unicode font
     */
    @SuppressWarnings("UnnecessaryUnicodeEscape")
    private static final String MCFONT_CHARS = "\u00c0\u00c1\u00c2\u00c8\u00ca\u00cb\u00cd\u00d3\u00d4\u00d5\u00da\u00df\u00e3\u00f5\u011f\u0130\u0131\u0152\u0153\u015e\u015f\u0174\u0175\u017e\u0207\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&\'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u2302\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5\u00c9\u00e6\u00c6\u00f4\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00f8\u00a3\u00d8\u00d7\u0192\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1\u00aa\u00ba\u00bf\u00ae\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255d\u255c\u255b\u2510\u2514\u2534\u252c\u251c\u2500\u253c\u255e\u255f\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u2567\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256b\u256a\u2518\u250c\u2588\u2584\u258c\u2590\u2580\u03b1\u03b2\u0393\u03c0\u03a3\u03c3\u03bc\u03c4\u03a6\u0398\u03a9\u03b4\u221e\u2205\u2208\u2229\u2261\u00b1\u2265\u2264\u2320\u2321\u00f7\u2248\u00b0\u2219\u00b7\u221a\u207f\u00b2\u25a0\u0000";

    private FontProviderMC() {}
    private static class InstLoader {
        static final FontProviderMC instance = new FontProviderMC();
        static final FontProviderMC instanceSGA = new FontProviderMC();
    }
    public static FontProviderMC get(boolean isSGA) {
        return (isSGA ? InstLoader.instanceSGA : InstLoader.instance);
    }

    private static final Char2ShortOpenHashMap MCFONT_ASCII_MAP = new Char2ShortOpenHashMap();
    ResourceLocation locationFontTexture = null;
    private final RandomXoshiro256StarStar fontRandom = new RandomXoshiro256StarStar();
    public int[] charWidth;

    static {
        for (short i = 0; i < MCFONT_CHARS.length(); i++) {
            MCFONT_ASCII_MAP.put(MCFONT_CHARS.charAt(i), i);
        }
    }

    public int lookupMcFontPosition(char ch) {
        if (isGlyphAvailable(ch)) {
            return MCFONT_ASCII_MAP.get(ch);
        }
        return -1;
    }

    @Override
    public boolean isGlyphAvailable(char chr) {
        return MCFONT_ASCII_MAP.containsKey(chr);
    }

    public char getRandomReplacement(char chr){
        int lutIndex = lookupMcFontPosition(chr);
        if (lutIndex != -1) {
            int randomReplacementIndex;
            do {
                randomReplacementIndex = fontRandom.nextInt(this.charWidth.length);
            } while (this.charWidth[lutIndex] != this.charWidth[randomReplacementIndex]);

            lutIndex = randomReplacementIndex;
            return MCFONT_CHARS.charAt(lutIndex);
        }
        return chr;
    }

    @Override
    public float getUStart(char chr) {
        int lutIndex = lookupMcFontPosition(chr);
        return ((lutIndex % 16) * 8) / 128.0F;
    }

    @Override
    public float getVStart(char chr) {
        int lutIndex = lookupMcFontPosition(chr);
        return (float) ((lutIndex / 16) * 8) / 128.0F;
    }

    @Override
    public float getXAdvance(char chr) {
        int lutIndex = lookupMcFontPosition(chr);
        return charWidth[lutIndex];
    }

    @Override
    public float getGlyphW(char chr) {
        int lutIndex = lookupMcFontPosition(chr);
        return charWidth[lutIndex] - 0.01F;
    }

    @Override
    public float getUSize(char chr) {
        int lutIndex = lookupMcFontPosition(chr);
        return (charWidth[lutIndex] - 1.01F) / 128.0F;
    }

    @Override
    public float getVSize(char chr) {
        return 7.99F / 128.0F;
    }

    @Override
    public float getShadowOffset() {
        return FontConfig.fontShadowOffset;
    }

    @Override
    public ResourceLocation getTexture(char chr) {
        return locationFontTexture;
    }

    @Override
    public float getYScaleMultiplier() {
        return 1;
    }
}
