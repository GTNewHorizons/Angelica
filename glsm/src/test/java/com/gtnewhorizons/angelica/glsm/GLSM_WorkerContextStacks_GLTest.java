package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@GLCoreTest
public class GLSM_WorkerContextStacks_GLTest {

    @Test
    void enableBitBracketRestoresOnWorkerContext() {
        GLStateManager.enterWorkerContext();
        try {
            GLStateManager.glDisable(GL11.GL_CULL_FACE);

            final int d = GLStateManager.pushState(StateSet.forMask(GL11.GL_ENABLE_BIT));
            GLStateManager.glEnable(GL11.GL_CULL_FACE);
            GLStateManager.popStateTo(d);

            assertFalse(GLStateManager.glIsEnabled(GL11.GL_CULL_FACE), "cull face restored on the worker context");
        } finally {
            GLStateManager.glDisable(GL11.GL_CULL_FACE);
            GLStateManager.exitWorkerContext();
        }
    }

    @Test
    void colorBufferBitBracketRestoresOnWorkerContext() {
        GLStateManager.enterWorkerContext();
        try {
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);

            final int d = GLStateManager.pushState(StateSet.forMask(GL11.GL_COLOR_BUFFER_BIT));
            GLStateManager.enableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GLStateManager.popStateTo(d);

            assertFalse(GLStateManager.glIsEnabled(GL11.GL_BLEND), "blend enable restored on the worker context");
            assertEquals(GL11.GL_ONE, GLStateManager.getBlendState().getSrcRgb(), "blend src rgb restored on the worker context");
        } finally {
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
            GLStateManager.exitWorkerContext();
        }
    }

    @Test
    void workerResetLeavesPrimaryContextUntouched() {
        GLStateManager.glDisable(GL11.GL_CULL_FACE);
        final int primaryDepthBefore = GLStateManager.getAttribDepth();

        GLStateManager.enterWorkerContext();
        try {
            GLStateManager.pushState(StateSet.forMask(GL11.GL_ENABLE_BIT));
            GLStateManager.glEnable(GL11.GL_CULL_FACE);

            GLStateManager.reset();

            assertEquals(0, GLStateManager.getAttribDepth(), "worker attrib depth drained by its own reset");
        } finally {
            GLStateManager.exitWorkerContext();
        }

        assertEquals(primaryDepthBefore, GLStateManager.getAttribDepth(), "primary context depth untouched by the worker reset");
        assertFalse(GLStateManager.glIsEnabled(GL11.GL_CULL_FACE), "primary context cull face untouched by the worker reset");
    }

    @Test
    void stackIdsEqualAcrossContexts() {
        final GLContextState primary = GLStateManager.ctx();
        final GLContextState worker = GLStateManager.enterWorkerContext();
        try {
            final Object[] primaryStacks = {
                primary.blendState, primary.depthState, primary.colorMask, primary.blendMode,
                primary.cullState, primary.textures.getTextureUnitBindings(1), primary.polygonState
            };
            final Object[] workerStacks = {
                worker.blendState, worker.depthState, worker.colorMask, worker.blendMode,
                worker.cullState, worker.textures.getTextureUnitBindings(1), worker.polygonState
            };

            final int[] primaryIds = new int[primaryStacks.length];
            final int[] workerIds = new int[workerStacks.length];
            for (int i = 0; i < primaryStacks.length; i++) {
                primaryIds[i] = Reflect.invoke(primaryStacks[i], "stackId", new Class[0]);
                workerIds[i] = Reflect.invoke(workerStacks[i], "stackId", new Class[0]);
            }

            assertArrayEquals(primaryIds, workerIds, "stack ids must match between the primary and worker contexts for identically-constructed stacks");
        } finally {
            GLStateManager.exitWorkerContext();
        }
    }
}
