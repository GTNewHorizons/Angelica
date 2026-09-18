package com.gtnewhorizons.angelica.client.font;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry is global static state with no unregister, so each test claims its own letter from
 * the free set (h, i, j, p, s, t, w, y).
 */
class FontEffectRegistryTest {

    private static final CustomGlyphEffect NO_OP = (argb, shadowPass, glyphIndex) -> argb;

    @Test
    void reservedCodesAreRejected() {
        for (char code : "abcdefgklmnoqruvxz".toCharArray()) {
            assertFalse(FontEffectRegistry.register(code, NO_OP), "reserved code accepted: " + code);
        }
        assertFalse(FontEffectRegistry.register('0', NO_OP));
        assertFalse(FontEffectRegistry.register('§', NO_OP));
    }

    @Test
    void nullEffectIsRejected() {
        assertFalse(FontEffectRegistry.register('p', null));
        assertFalse(FontEffectRegistry.isRegistered('p'));
    }

    @Test
    void validCodeRegistersOnce() {
        CustomGlyphEffect effect = (argb, shadowPass, glyphIndex) -> ~argb;
        assertTrue(FontEffectRegistry.register('s', effect));
        assertFalse(FontEffectRegistry.register('s', NO_OP), "duplicate registration accepted");
        assertTrue(FontEffectRegistry.isRegistered('s'));
        assertTrue(FontEffectRegistry.hasAny());
        assertSame(effect, FontEffectRegistry.get(Long.numberOfTrailingZeros(FontEffectRegistry.bit('s'))));
    }

    @Test
    void uppercaseClaimsTheLowercaseLetter() {
        assertTrue(FontEffectRegistry.register('T', NO_OP));
        assertTrue(FontEffectRegistry.isRegistered('t'));
    }

    @Test
    void bitMatchesLetterIndex() {
        assertEquals(1L << ('w' - 'a'), FontEffectRegistry.bit('w'));
    }

    @Test
    void unimplementedHooksDoNothing() {
        assertEquals(0.0f, NO_OP.offsetX(3));
        assertEquals(0.0f, NO_OP.offsetY(3));
        assertEquals(0, NO_OP.backgroundColor(3));
    }
}
