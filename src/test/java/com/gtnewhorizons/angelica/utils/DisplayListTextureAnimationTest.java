package com.gtnewhorizons.angelica.utils;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.recording.GLCommand;
import com.gtnewhorizons.angelica.mixins.interfaces.IPatchedTextureAtlasSprite;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@GLCoreTest
class DisplayListTextureAnimationTest {
    @Test
    void cachedMeshMarksEachCapturedSpriteOncePerUse() {
        final IPatchedTextureAtlasSprite sprite = mock(IPatchedTextureAtlasSprite.class);
        final IPatchedTextureAtlasSprite overlay = mock(IPatchedTextureAtlasSprite.class);
        final AnimationsRenderUtils.SpriteCapture capture = AnimationsRenderUtils.captureSprites();
        try (capture) {
            for (int vertex = 0; vertex < 1000; vertex++) AnimationsRenderUtils.recordSpriteUsage(sprite);
            AnimationsRenderUtils.recordSpriteUsage(overlay);
        }
        for (int frame = 0; frame < 3; frame++) {
            clearInvocations(sprite, overlay);
            capture.markUsed();
            verify(sprite).angelica$markNeedsAnimationUpdate();
            verify(overlay).angelica$markNeedsAnimationUpdate();
        }
    }

    @Test
    void nestedCaptureRestoresItsParentEvenWhenRenderingThrows() {
        final IPatchedTextureAtlasSprite nested = mock(IPatchedTextureAtlasSprite.class);
        final IPatchedTextureAtlasSprite outer = mock(IPatchedTextureAtlasSprite.class);
        final IPatchedTextureAtlasSprite unrelated = mock(IPatchedTextureAtlasSprite.class);
        final AnimationsRenderUtils.SpriteCapture capture = AnimationsRenderUtils.captureSprites();
        try (capture) {
            assertThrows(IllegalStateException.class, () -> {
                try (var child = AnimationsRenderUtils.captureSprites()) {
                    AnimationsRenderUtils.recordSpriteUsage(nested);
                    throw new IllegalStateException("renderer failed");
                }
            });
            AnimationsRenderUtils.recordSpriteUsage(outer);
        }
        AnimationsRenderUtils.recordSpriteUsage(unrelated);
        capture.markUsed();
        verify(nested).angelica$markNeedsAnimationUpdate();
        verify(outer).angelica$markNeedsAnimationUpdate();
        verifyNoInteractions(unrelated);
    }

    @Test
    void cachedDrawMarksItsSpriteEveryFrame() {
        final IPatchedTextureAtlasSprite sprite = mock(IPatchedTextureAtlasSprite.class);
        for (int mode : new int[] { GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE }) {
            final int list = GLStateManager.glGenLists(1);
            try {
                GLStateManager.glNewList(list, mode);
                for (int vertex = 0; vertex < 1000; vertex++) AnimationsRenderUtils.recordSpriteUsage(sprite);
                GLStateManager.glEndList();
                assertEquals(1, DisplayListManager.getDisplayList(list).getCommandCounts().get(GLCommand.COMPLEX_REF),
                    "repeated UV accesses should record only one notification");
                for (int frame = 0; frame < 3; frame++) {
                    clearInvocations(sprite);
                    GLStateManager.glCallList(list);
                    verify(sprite).angelica$markNeedsAnimationUpdate();
                }
            } finally {
                if (DisplayListManager.isRecording()) GLStateManager.glEndList();
                GLStateManager.glDeleteLists(list, 1);
            }
        }
    }
}
