package com.gtnewhorizons.angelica.client.font;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Maps unused {@code §} format-code letters to {@link CustomGlyphEffect}s supplied by other mods
 * (e.g. HexText's ignite and shake). Letters already used by vanilla formatting or the batched
 * font renderer's own grammar cannot be claimed.
 */
public final class FontEffectRegistry {

    private static final Logger LOGGER = LogManager.getLogger("AngelicaFontEffects");

    /** Letters with a meaning in vanilla formatting or the batched renderer's grammar. */
    private static final String RESERVED_CODES = "abcdefgklmnoqruvxz";

    private static final CustomGlyphEffect[] EFFECTS = new CustomGlyphEffect[26];
    private static volatile boolean anyRegistered = false;

    private FontEffectRegistry() {}

    /**
     * Claims {@code code} (a-z, case-insensitive) for the given effect.
     *
     * @return false when the letter is reserved, already claimed, or invalid
     */
    public static synchronized boolean register(char code, CustomGlyphEffect effect) {
        final char lower = Character.toLowerCase(code);
        if (lower < 'a' || lower > 'z' || RESERVED_CODES.indexOf(lower) != -1 || effect == null) {
            LOGGER.warn("Rejected font effect registration for reserved or invalid code '{}'", code);
            return false;
        }
        if (EFFECTS[lower - 'a'] != null) {
            LOGGER.warn("Rejected font effect registration for already-claimed code '{}'", code);
            return false;
        }
        EFFECTS[lower - 'a'] = effect;
        anyRegistered = true;
        return true;
    }

    public static boolean hasAny() {
        return anyRegistered;
    }

    public static boolean isRegistered(char lowerCode) {
        return anyRegistered && lowerCode >= 'a' && lowerCode <= 'z' && EFFECTS[lowerCode - 'a'] != null;
    }

    static long bit(char lowerCode) {
        return 1L << (lowerCode - 'a');
    }

    static CustomGlyphEffect get(int bitIndex) {
        return EFFECTS[bitIndex];
    }
}
