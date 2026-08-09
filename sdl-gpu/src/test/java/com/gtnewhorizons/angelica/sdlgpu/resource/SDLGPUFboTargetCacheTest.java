package com.gtnewhorizons.angelica.sdlgpu.resource;


import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SDLGPUFboTargetCacheTest {

    @Test
    void freshFboState_hasNoColorAndZeroTarget() {
        final FboState fbo = new FboState();
        assertTrue(fbo.targetsDirty, "fresh state must compute on first read");
        fbo.recomputeTargets();
        assertFalse(fbo.targetsDirty);
        assertFalse(fbo.hasAnyColor);
        assertEquals(0L, fbo.primaryTarget);
    }

    @Test
    void depthOnly_primaryTargetIsDepth() {
        final FboState fbo = new FboState();
        fbo.depthTexture = 0xDDDDL;
        fbo.recomputeTargets();
        assertFalse(fbo.hasAnyColor);
        assertEquals(0xDDDDL, fbo.primaryTarget);
    }

    @Test
    void singleColor_primaryTargetIsColor() {
        final FboState fbo = new FboState();
        fbo.colorTextures[0] = 0xAAAAL;
        fbo.depthTexture = 0xDDDDL;
        // default drawBuffers = {0}
        fbo.recomputeTargets();
        assertTrue(fbo.hasAnyColor);
        assertEquals(0xAAAAL, fbo.primaryTarget);
    }

    @Test
    void disabledDrawBuffer_doesNotCount() {
        final FboState fbo = new FboState();
        fbo.colorTextures[0] = 0xAAAAL;
        fbo.colorTextures[1] = 0xBBBBL;
        fbo.drawBuffers = new int[]{-1, 1}; // GL_NONE then attachment 1
        fbo.depthTexture = 0xDDDDL;
        fbo.recomputeTargets();
        assertTrue(fbo.hasAnyColor);
        assertEquals(0xBBBBL, fbo.primaryTarget, "first enabled drawBuffer is index 1");
    }

    @Test
    void allDrawBuffersDisabled_falsThroughToDepth() {
        final FboState fbo = new FboState();
        fbo.colorTextures[0] = 0xAAAAL;
        fbo.drawBuffers = new int[]{-1};
        fbo.depthTexture = 0xDDDDL;
        fbo.recomputeTargets();
        assertFalse(fbo.hasAnyColor);
        assertEquals(0xDDDDL, fbo.primaryTarget);
    }

    @Test
    void mrtFirstColorWins_evenWhenMultipleAttached() {
        final FboState fbo = new FboState();
        fbo.colorTextures[0] = 0xAAAAL;
        fbo.colorTextures[1] = 0xBBBBL;
        fbo.colorTextures[2] = 0xCCCCL;
        fbo.drawBuffers = new int[]{0, 1, 2};
        fbo.recomputeTargets();
        assertTrue(fbo.hasAnyColor);
        assertEquals(0xAAAAL, fbo.primaryTarget);
    }

    @Test
    void emptyDrawBuffers_noColorEvenIfBound() {
        final FboState fbo = new FboState();
        fbo.colorTextures[0] = 0xAAAAL;
        fbo.drawBuffers = new int[0];
        fbo.depthTexture = 0xDDDDL;
        fbo.recomputeTargets();
        assertFalse(fbo.hasAnyColor);
        assertEquals(0xDDDDL, fbo.primaryTarget);
    }

    @Test
    void detachColor_zeroesSlotAndShrinksCount() {
        final FboState fbo = new FboState();
        fbo.colorTextures[0] = 0xAAAAL; fbo.colorGlIds[0] = 10; fbo.colorFormats[0] = 100;
        fbo.colorTextures[1] = 0xBBBBL; fbo.colorGlIds[1] = 11; fbo.colorFormats[1] = 101;
        fbo.colorTextures[2] = 0xCCCCL; fbo.colorGlIds[2] = 12; fbo.colorFormats[2] = 102;
        fbo.colorAttachmentCount = 3;
        fbo.cachedFormatsDirty = false;
        fbo.recomputeTargets();
        assertFalse(fbo.targetsDirty);

        fbo.detachColor(2);

        assertEquals(0L, fbo.colorTextures[2]);
        assertEquals(0, fbo.colorGlIds[2]);
        assertEquals(0, fbo.colorFormats[2]);
        assertEquals(2, fbo.colorAttachmentCount, "topmost detach shrinks count to highest remaining + 1");
        assertTrue(fbo.targetsDirty);
        assertTrue(fbo.cachedFormatsDirty);
    }

    @Test
    void detachColor_middleSlotKeepsCount() {
        final FboState fbo = new FboState();
        fbo.colorTextures[0] = 0xAAAAL;
        fbo.colorTextures[2] = 0xCCCCL;
        fbo.colorAttachmentCount = 3;

        fbo.detachColor(0);

        assertEquals(0L, fbo.colorTextures[0]);
        assertEquals(0xCCCCL, fbo.colorTextures[2], "non-detached slot untouched");
        assertEquals(3, fbo.colorAttachmentCount, "count stays while slot 2 occupied");
        assertTrue(fbo.targetsDirty);
        assertTrue(fbo.cachedFormatsDirty);
    }

    @Test
    void detachDepth_zeroesFields() {
        final FboState fbo = new FboState();
        fbo.depthTexture = 0xDDDDL;
        fbo.depthGlId = 42;
        fbo.depthFormat = 999;
        fbo.recomputeTargets();
        assertFalse(fbo.targetsDirty);

        fbo.detachDepth();

        assertEquals(0L, fbo.depthTexture);
        assertEquals(0, fbo.depthGlId);
        assertEquals(0, fbo.depthFormat);
        assertTrue(fbo.targetsDirty);
    }

    @Test
    void detachColor_outOfBounds_noOp() {
        final FboState fbo = new FboState();
        fbo.colorTextures[0] = 0xAAAAL;
        fbo.colorAttachmentCount = 1;
        fbo.cachedFormatsDirty = false;
        fbo.recomputeTargets();

        fbo.detachColor(-1);
        fbo.detachColor(ContextState.MAX_COLOR_ATTACHMENTS);

        assertEquals(0xAAAAL, fbo.colorTextures[0]);
        assertEquals(1, fbo.colorAttachmentCount);
        assertFalse(fbo.targetsDirty);
        assertFalse(fbo.cachedFormatsDirty);
    }
}
