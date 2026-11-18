package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.hooks.TextureUtilHooks;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class GLSM_ExitTextureFilter_GLTest {
    private int outer, texture, otherTexture, savedMin, savedMag;
    private float savedAniso;
    private boolean failOperation;

    @BeforeEach
    void setUp() {
        outer = GLSMAttribStack.pushState(StateSet.forMask(GL11.GL_TEXTURE_BIT));
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        texture = GLSMTextures.glGenTextures();
        otherTexture = GLSMTextures.glGenTextures();
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, otherTexture);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    }

    @AfterEach
    void tearDown() {
        GLStateManager.cancelExitCapture();
        TextureUtilHooks.clearSavedFilterTexture();
        GLSMTextures.glDeleteTextures(texture);
        GLSMTextures.glDeleteTextures(otherTexture);
        GLSMAttribStack.popStateTo(outer);
    }

    @Test
    void nearestFilterSaveAndRestoreIsAllowedAndApplyLeavesFiltersAlone() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        assertTrue(save(false, false, 1));
        assertTrue(GLStateManager.isExitCaptureActive());
        GLStateManager.requireExitTextureFilter(otherTexture, false);
        GLStateManager.requireExitTextureFilter(texture, true);
        restore();
        assertTrue(GLStateManager.finishExitCapture(state));
        assertFilters(GL11.GL_LINEAR_MIPMAP_LINEAR, GL11.GL_LINEAR);
        assertEquals(0, GLStateManager.ctx().savedFilterTextureId);

        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        assertTrue(GLStateManager.canApplyExitState(state));
        GLStateManager.applyExitState(state);
        assertFilters(GL11.GL_LINEAR, GL11.GL_NEAREST);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void drawingTheTemporarilyFilteredTextureRequiresNearestFiltering(boolean nearest) {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        save(false, false, 1);
        GLStateManager.requireExitTextureFilter(texture, nearest);
        restore();
        assertEquals(nearest, GLStateManager.finishExitCapture(state));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void savingAnythingButNearestFilteringRejects(int request) {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        assertFalse(save(request == 0, request == 1, request == 2 ? 2 : 1));
        restore();
        assertFalse(GLStateManager.finishExitCapture(state));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void textureParameterReadsAndWritesStillReject(int operation) {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        save(false, false, 1);
        switch (operation) {
            case 0 -> GLSMTextures.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            case 1 -> GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            case 2 -> GLSMTextures.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            default -> throw new AssertionError();
        }
        restore();
        assertFalse(GLStateManager.finishExitCapture(state));
    }

    @Test
    void missingRestoreRejectsAndDoesNotAffectTheNextCapture() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        save(false, false, 1);
        assertFalse(GLStateManager.finishExitCapture(state));
        restore();
        GLStateManager.beginExitCapture(state);
        assertTrue(GLStateManager.finishExitCapture(state));
    }

    @Test
    void secondSaveBeforeRestoreRejects() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        assertTrue(save(false, false, 1));
        assertFalse(save(false, false, 1));
        restore();
        assertFalse(GLStateManager.finishExitCapture(state));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void restoreMustUseTheSameTextureAndOriginalParameters(boolean wrongTexture) {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        save(false, false, 1);
        if (wrongTexture) GLStateManager.ctx().savedFilterTextureId = otherTexture;
        else savedMin = GL11.GL_NEAREST;
        restore();
        assertFalse(GLStateManager.finishExitCapture(state));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedSaveOrRestoreRejectsAndTheNextCaptureStillWorks(boolean restoring) {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        if (restoring) save(false, false, 1);
        failOperation = true;
        assertThrows(IllegalStateException.class, () -> {
            if (restoring) restore();
            else save(false, false, 1);
        });
        assertFalse(GLStateManager.finishExitCapture(state));
        TextureUtilHooks.clearSavedFilterTexture();
        GLStateManager.beginExitCapture(state);
        assertTrue(GLStateManager.isExitCaptureActive());
        assertTrue(GLStateManager.finishExitCapture(state));
    }

    @Test
    void openFilterSaveBlocksCapturingAndApplying() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        assertTrue(GLStateManager.finishExitCapture(state));
        save(false, false, 1);
        assertFalse(GLStateManager.canApplyExitState(state));
        final ExitState nested = new ExitState();
        GLStateManager.beginExitCapture(nested);
        assertFalse(GLStateManager.finishExitCapture(nested));
        restore();
        assertTrue(GLStateManager.canApplyExitState(state));
    }

    private boolean save(boolean blurred, boolean mipmap, float aniso) {
        final boolean captured = TextureUtilHooks.beginFilterSave(blurred, mipmap, aniso);
        try {
            savedMin = GLSMTextures.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            savedMag = GLSMTextures.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
            savedAniso = GLSMTextures.glGetTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
                blurred ? (mipmap ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR)
                    : (mipmap ? GL11.GL_NEAREST_MIPMAP_LINEAR : GL11.GL_NEAREST));
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, blurred ? GL11.GL_LINEAR : GL11.GL_NEAREST);
            GLSMTextures.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
            if (failOperation) throw new IllegalStateException("filter save failed");
        } catch (RuntimeException | Error failure) {
            TextureUtilHooks.filterFailed(captured);
            throw failure;
        } finally {
            TextureUtilHooks.endFilterSave(captured);
        }
        return captured;
    }

    private void restore() {
        final boolean captured = TextureUtilHooks.beginFilterRestore();
        try {
            TextureUtilHooks.restoreFilter(savedMin, savedMag);
            TextureUtilHooks.restoreAniso(savedAniso);
            if (failOperation) throw new IllegalStateException("filter restore failed");
        } catch (RuntimeException | Error failure) {
            TextureUtilHooks.filterFailed(captured);
            throw failure;
        } finally {
            TextureUtilHooks.endFilterRestore(captured);
        }
    }

    private void assertFilters(int min, int mag) {
        final var info = TextureInfoCache.INSTANCE.getInfo(texture);
        assertEquals(min, info.getMinFilter());
        assertEquals(mag, info.getMagFilter());
    }
}
