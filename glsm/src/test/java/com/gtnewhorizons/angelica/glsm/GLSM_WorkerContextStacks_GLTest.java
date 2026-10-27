package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyIsEnabled;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@GLCoreTest
public class GLSM_WorkerContextStacks_GLTest {

    @Test
    void enableBitBracketRestoresOnWorkerContext() {
        GLStateManager.enterWorkerContext();
        try {
            GLSMEnable.glDisable(GL11.GL_CULL_FACE);

            final int d = GLSMAttribStack.pushState(StateSet.forMask(GL11.GL_ENABLE_BIT));
            GLSMEnable.glEnable(GL11.GL_CULL_FACE);
            GLSMAttribStack.popStateTo(d);

            assertFalse(GLSMEnable.glIsEnabled(GL11.GL_CULL_FACE), "cull face restored on the worker context");
            verifyIsEnabled(GL11.GL_CULL_FACE, false, "driver cull face restored on the worker context");
        } finally {
            GLSMEnable.glDisable(GL11.GL_CULL_FACE);
            GLStateManager.exitWorkerContext();
        }
    }

    @Test
    void colorBufferBitBracketRestoresOnWorkerContext() {
        GLStateManager.enterWorkerContext();
        try {
            GLSMBlend.disableBlend();
            GLSMBlend.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);

            final int d = GLSMAttribStack.pushState(StateSet.forMask(GL11.GL_COLOR_BUFFER_BIT));
            GLSMBlend.enableBlend();
            GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GLSMAttribStack.popStateTo(d);

            assertFalse(GLSMEnable.glIsEnabled(GL11.GL_BLEND), "blend enable restored on the worker context");
            verifyIsEnabled(GL11.GL_BLEND, false, "driver blend enable restored on the worker context");
            assertEquals(GL11.GL_ONE, GLSMBlend.getBlendState().getSrcRgb(), "blend src rgb restored on the worker context");
            assertEquals(GL11.GL_ONE, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), "driver blend src rgb restored on the worker context");
        } finally {
            GLSMBlend.disableBlend();
            GLSMBlend.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
            GLStateManager.exitWorkerContext();
        }
    }

    @Test
    void workerResetLeavesPrimaryContextUntouched() {
        GLSMEnable.glDisable(GL11.GL_CULL_FACE);
        final int primaryDepthBefore = GLSMAttribStack.getAttribDepth();

        GLStateManager.enterWorkerContext();
        try {
            GLSMAttribStack.pushState(StateSet.forMask(GL11.GL_ENABLE_BIT));
            GLSMEnable.glEnable(GL11.GL_CULL_FACE);

            GLStateManager.reset();

            assertEquals(0, GLSMAttribStack.getAttribDepth(), "worker attrib depth drained by its own reset");
        } finally {
            GLStateManager.exitWorkerContext();
        }

        assertEquals(primaryDepthBefore, GLSMAttribStack.getAttribDepth(), "primary context depth untouched by the worker reset");
        assertFalse(GLSMEnable.glIsEnabled(GL11.GL_CULL_FACE), "primary context cull face untouched by the worker reset");
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
