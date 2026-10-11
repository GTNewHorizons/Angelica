package com.gtnewhorizons.angelica.client.rendering;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.*;

class DeferredDrawBatcherTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "basic, 42, 0x0302, 0x0001, true, false",
        "zero fields, 0, 0, 0, false, true",
        "largest 20-bit texture id, 1048575, 0x0302, 0x0303, true, true",
    })
    void stateKey_roundtrip(String name, int textureId, int srcRgb, int dstRgb, boolean blend, boolean depthMask) {
        long key = DeferredDrawBatcher.packStateKey(textureId, srcRgb, dstRgb, blend, depthMask, true, true);
        assertEquals(textureId, DeferredDrawBatcher.unpackTextureId(key));
        assertEquals(srcRgb, DeferredDrawBatcher.unpackSrcRgb(key));
        assertEquals(dstRgb, DeferredDrawBatcher.unpackDstRgb(key));
        assertEquals(blend, DeferredDrawBatcher.unpackBlendEnabled(key));
        assertEquals(depthMask, DeferredDrawBatcher.unpackDepthMask(key));
    }

    @Test
    void stateKey_differentKeysNotEqual() {
        long keyA = DeferredDrawBatcher.packStateKey(42, GL11.GL_SRC_ALPHA, GL11.GL_ONE, true, false, true, true);
        long keyB = DeferredDrawBatcher.packStateKey(43, GL11.GL_SRC_ALPHA, GL11.GL_ONE, true, false, true, true);
        long keyC = DeferredDrawBatcher.packStateKey(42, GL11.GL_ONE, GL11.GL_ONE, true, false, true, true);
        assertNotEquals(keyA, keyB, "Different texture IDs should produce different keys");
        assertNotEquals(keyA, keyC, "Different blend funcs should produce different keys");
    }

    @Test
    void stateKey_blendConstants() {
        // Verify all common GL blend constants roundtrip correctly
        int[] blendConstants = {
            GL11.GL_ZERO,           // 0x0000
            GL11.GL_ONE,            // 0x0001
            GL11.GL_SRC_ALPHA,      // 0x0302
            GL11.GL_ONE_MINUS_SRC_ALPHA, // 0x0303
            GL11.GL_DST_ALPHA,      // 0x0304
            GL11.GL_ONE_MINUS_DST_ALPHA, // 0x0305
        };

        for (int src : blendConstants) {
            for (int dst : blendConstants) {
                long key = DeferredDrawBatcher.packStateKey(1, src, dst, true, true, true, true);
                assertEquals(src, DeferredDrawBatcher.unpackSrcRgb(key), "srcRgb roundtrip failed for 0x" + Integer.toHexString(src));
                assertEquals(dst, DeferredDrawBatcher.unpackDstRgb(key), "dstRgb roundtrip failed for 0x" + Integer.toHexString(dst));
            }
        }
    }

    @Test
    void stateKey_textureEnableRoundtrip() {
        // tex0=true, tex1=false
        long key1 = DeferredDrawBatcher.packStateKey(42, 0x0302, 0x0001, true, true, true, false);
        assertTrue(DeferredDrawBatcher.unpackTex0Enabled(key1));
        assertFalse(DeferredDrawBatcher.unpackTex1Enabled(key1));
        assertEquals(42, DeferredDrawBatcher.unpackTextureId(key1));

        // tex0=false, tex1=true
        long key2 = DeferredDrawBatcher.packStateKey(42, 0x0302, 0x0001, true, true, false, true);
        assertFalse(DeferredDrawBatcher.unpackTex0Enabled(key2));
        assertTrue(DeferredDrawBatcher.unpackTex1Enabled(key2));
        assertEquals(42, DeferredDrawBatcher.unpackTextureId(key2));

        // Enable bits don't interfere with existing fields
        long keyBothOff = DeferredDrawBatcher.packStateKey(42, 0x0302, 0x0001, true, true, false, false);
        long keyBothOn  = DeferredDrawBatcher.packStateKey(42, 0x0302, 0x0001, true, true, true, true);
        assertEquals(DeferredDrawBatcher.unpackTextureId(keyBothOff), DeferredDrawBatcher.unpackTextureId(keyBothOn));
        assertEquals(DeferredDrawBatcher.unpackSrcRgb(keyBothOff), DeferredDrawBatcher.unpackSrcRgb(keyBothOn));
        assertEquals(DeferredDrawBatcher.unpackDstRgb(keyBothOff), DeferredDrawBatcher.unpackDstRgb(keyBothOn));
        assertEquals(DeferredDrawBatcher.unpackBlendEnabled(keyBothOff), DeferredDrawBatcher.unpackBlendEnabled(keyBothOn));
        assertEquals(DeferredDrawBatcher.unpackDepthMask(keyBothOff), DeferredDrawBatcher.unpackDepthMask(keyBothOn));

        // Different tex enable states produce different keys
        assertNotEquals(key1, key2, "Different texture enable states should produce different keys");
    }
}
