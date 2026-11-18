package com.gtnewhorizons.angelica.client.font;

public interface FontProvider {

    /** Tries before {@link #getRandomReplacement} gives up and leaves the character alone. */
    int RANDOM_GLYPH_TRIES = 64;

    /**
     * For use with §k. Should fetch a character of the same width as provided, and one this
     * provider can draw, since the provider is picked for the original character.
     */
    char getRandomReplacement(char chr);
    boolean isGlyphAvailable(char chr);
    float getUStart(char chr);
    float getVStart(char chr);
    float getXAdvance(char chr);
    float getGlyphW(char chr);
    float getUSize(char chr);
    float getVSize(char chr);
    float getShadowOffset();
    int getTexture(char chr);
    float getYScaleMultiplier();

    /** Vertical offset in font pixels, applied after the glyph is centered on the line. */
    default float getBaselineShift() { return 0.0f; }
}
