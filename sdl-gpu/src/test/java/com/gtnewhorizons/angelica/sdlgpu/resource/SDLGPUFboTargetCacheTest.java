package com.gtnewhorizons.angelica.sdlgpu.resource;


import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    private static Arguments row(String name, Consumer<FboState> setup, boolean anyColor, long primary) {
        return Arguments.of(name, setup, anyColor, primary);
    }

    private static Stream<Arguments> recomputedTargets() {
        return Stream.of(
            row("depth only", f -> f.depthTexture = 0xDDDDL, false, 0xDDDDL),
            row("single color over depth, default drawBuffers", f -> {
                f.colorTextures[0] = 0xAAAAL;
                f.depthTexture = 0xDDDDL;
            }, true, 0xAAAAL),
            row("disabled slot 0, first enabled drawBuffer is index 1", f -> {
                f.colorTextures[0] = 0xAAAAL;
                f.colorTextures[1] = 0xBBBBL;
                f.drawBuffers = new int[]{-1, 1};
                f.depthTexture = 0xDDDDL;
            }, true, 0xBBBBL),
            row("all drawBuffers disabled falls through to depth", f -> {
                f.colorTextures[0] = 0xAAAAL;
                f.drawBuffers = new int[]{-1};
                f.depthTexture = 0xDDDDL;
            }, false, 0xDDDDL),
            row("MRT first color wins", f -> {
                f.colorTextures[0] = 0xAAAAL;
                f.colorTextures[1] = 0xBBBBL;
                f.colorTextures[2] = 0xCCCCL;
                f.drawBuffers = new int[]{0, 1, 2};
            }, true, 0xAAAAL),
            row("empty drawBuffers means no color even if bound", f -> {
                f.colorTextures[0] = 0xAAAAL;
                f.drawBuffers = new int[0];
                f.depthTexture = 0xDDDDL;
            }, false, 0xDDDDL));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recomputedTargets")
    void recomputeTargets_picksPrimaryTarget(String name, Consumer<FboState> setup, boolean anyColor, long primary) {
        final FboState fbo = new FboState();
        setup.accept(fbo);
        fbo.recomputeTargets();
        assertEquals(anyColor, fbo.hasAnyColor);
        assertEquals(primary, fbo.primaryTarget);
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
    void sameTextureAtAnotherLevelChangesStructuralHash() {
        final FboState fbo = new FboState();
        fbo.colorTextures[0] = 0xAAAAL;
        fbo.depthTexture = 0xDDDDL;
        fbo.recomputeTargets();
        final long level0 = fbo.structuralLayoutHash;

        fbo.colorLevels[0] = 1;
        fbo.recomputeTargets();
        final long colorLevel1 = fbo.structuralLayoutHash;
        assertNotEquals(level0, colorLevel1);

        fbo.colorLevels[0] = 0;
        fbo.depthLevel = 1;
        fbo.recomputeTargets();
        assertNotEquals(level0, fbo.structuralLayoutHash);
        assertNotEquals(colorLevel1, fbo.structuralLayoutHash);

        fbo.depthLevel = 0;
        fbo.recomputeTargets();
        assertEquals(level0, fbo.structuralLayoutHash);
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
