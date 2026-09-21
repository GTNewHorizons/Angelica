package com.gtnewhorizons.angelica.compat.draconicevolution;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import net.minecraft.client.renderer.entity.RenderItem;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.*;

@GLCoreTest
class PlacedItemRenderCompatTest {
    @Test
    void customAnimationRunsEveryFrameAndRestoresEligibilityAfterFailure() {
        final Matrix4f original = new Matrix4f(GLStateManager.getModelViewMatrix());
        final int[] frames = { 0 };
        BatchEligibility.begin(BatchEligibility.SAFE, GLStateManager.drawCalls);
        try {
            BatchEligibility.onPartQueued();
            for (int frame = 0; frame < 3; frame++) {
                assertEquals(BatchEligibility.DENIED, PlacedItemRenderCompat.renderWithBatchState(1, 2, 3,
                    BatchEligibility.DENIED, () -> {
                        assertFalse(BatchEligibility.batchingAllowed());
                        GLStateManager.glRotatef(frames[0]++ * 30, 0, 1, 0);
                        final long before = GLStateManager.drawCalls;
                        GLStateManager.drawCalls += 2;
                        BatchEligibility.onPartFallback(before, GLStateManager.drawCalls);
                        GLStateManager.drawCalls++;
                    }));
                assertTrue(BatchEligibility.batchingAllowed());
                assertTrue(original.equals(GLStateManager.getModelViewMatrix(), 1e-5f));
            }
            assertEquals(3, frames[0]);
            assertThrows(IllegalStateException.class, () -> PlacedItemRenderCompat.renderWithBatchState(1, 2, 3,
                BatchEligibility.DENIED, () -> {
                    GLStateManager.drawCalls++;
                    throw new IllegalStateException("renderer failed");
                }));
            assertTrue(BatchEligibility.batchingAllowed());
            assertTrue(original.equals(GLStateManager.getModelViewMatrix(), 1e-5f));
        } finally {
            assertEquals(BatchEligibility.SAFE, BatchEligibility.end(BatchEligibility.SAFE, GLStateManager.drawCalls));
        }
        assertFalse(BatchEligibility.batchingAllowed());
    }

    @Test
    void eachFrameRunsTheAnimationAndRestoresPlacementStateEvenOnFailure() {
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        final Matrix4f original = new Matrix4f(GLStateManager.getModelViewMatrix());
        final boolean originalFrame = RenderItem.renderInFrame;
        final boolean originalDepth = GLStateManager.getDepthTest().isEnabled();
        final int[] calls = { 0 };
        try {
            GLStateManager.glLoadIdentity();
            GLStateManager.enableDepthTest();
            RenderItem.renderInFrame = true;
            for (int frame = 0; frame < 3; frame++) {
                final int tick = frame;
                PlacedItemRenderCompat.render(1, 2, 3, () -> {
                    calls[0]++;
                    GLStateManager.glRotatef(tick * 30, 0, 1, 0);
                    assertTrue(new Matrix4f().translation(1, 2, 3).rotateY((float) Math.toRadians(tick * 30))
                        .equals(GLStateManager.getModelViewMatrix(), 1e-5f));
                    GLStateManager.disableDepthTest();
                    RenderItem.renderInFrame = false;
                });
                assertEquals(frame + 1, calls[0]);
                assertTrue(new Matrix4f().equals(GLStateManager.getModelViewMatrix(), 1e-5f));
                assertTrue(GL11.glIsEnabled(GL11.GL_DEPTH_TEST));
                assertTrue(RenderItem.renderInFrame);
            }
            assertThrows(IllegalStateException.class, () -> PlacedItemRenderCompat.render(1, 2, 3, () -> {
                RenderItem.renderInFrame = false;
                throw new IllegalStateException("renderer failed");
            }));
            assertTrue(new Matrix4f().equals(GLStateManager.getModelViewMatrix(), 1e-5f));
            assertTrue(RenderItem.renderInFrame);
        } finally {
            GLStateManager.setModelViewMatrix(original);
            GLStateManager.getDepthTest().setEnabled(originalDepth);
            RenderItem.renderInFrame = originalFrame;
        }
    }
}
