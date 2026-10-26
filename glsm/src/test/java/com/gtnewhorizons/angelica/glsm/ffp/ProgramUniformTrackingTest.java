package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@GLCoreTest
class ProgramUniformTrackingTest {

    private static Program buildProgram(boolean hasColor) {
        final VertexKey vk = VertexKey.fromState(hasColor, false, false, false, 0);
        final FragmentKey fk = FragmentKey.fromState();
        return Program.create(vk, fk, VertexShaderGenerator.generate(vk), FragmentShaderGenerator.generate(fk));
    }

    @Test
    void programSwitchWritesNothingStateChangeWritesOnce() {
        final Uniforms uniforms = new Uniforms();
        final Program a = buildProgram(true);
        final Program b = buildProgram(false);
        assertNotEquals(a.getProgramId(), b.getProgramId(), "distinct vertex keys must yield distinct programs");
        try {
            GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
            GLSMMatrices.glPushMatrix();
            GLSMMatrices.glLoadIdentity();
            GLSMMatrices.glTranslatef(1.0F, 2.0F, 3.0F);

            GL20.glUseProgram(a.getProgramId());
            uniforms.upload(GLStateManager.ctx());
            final int afterFirst = uniforms.blockWrites;

            GL20.glUseProgram(b.getProgramId());
            uniforms.upload(GLStateManager.ctx());
            GL20.glUseProgram(a.getProgramId());
            uniforms.upload(GLStateManager.ctx());
            assertEquals(afterFirst, uniforms.blockWrites, "program switches with unchanged state write nothing");

            GLSMMatrices.glTranslatef(4.0F, 0.0F, 0.0F);
            uniforms.upload(GLStateManager.ctx());
            assertEquals(afterFirst + 1, uniforms.blockWrites, "a gen bump writes exactly one block");

            uniforms.upload(GLStateManager.ctx());
            assertEquals(afterFirst + 1, uniforms.blockWrites, "clean draw writes nothing");

            GLSMMatrices.glPopMatrix();
        } finally {
            GL20.glUseProgram(0);
            a.destroy();
            b.destroy();
            uniforms.destroy();
        }
    }

    @Test
    void pushPopScopeWithoutNetChangeWritesNothing() {
        final Uniforms uniforms = new Uniforms();
        final Program a = buildProgram(true);
        try {
            GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
            GLSMMatrices.glPushMatrix();
            GLSMMatrices.glLoadIdentity();

            GL20.glUseProgram(a.getProgramId());
            uniforms.upload(GLStateManager.ctx());
            final int base = uniforms.blockWrites;

            GLSMAttribStack.glPushAttrib(GL11.GL_TRANSFORM_BIT);
            GLSMAttribStack.glPopAttrib();
            uniforms.upload(GLStateManager.ctx());
            assertEquals(base, uniforms.blockWrites, "push/pop with no change inside writes nothing");

            GLSMAttribStack.glPushAttrib(GL11.GL_TRANSFORM_BIT);
            GLSMMatrices.glTranslatef(7.0F, 0.0F, 0.0F);
            GLSMAttribStack.glPopAttrib();
            uniforms.upload(GLStateManager.ctx());
            assertEquals(base + 1, uniforms.blockWrites, "pop after change stages once");

            GLSMMatrices.glPopMatrix();
        } finally {
            GL20.glUseProgram(0);
            a.destroy();
            uniforms.destroy();
        }
    }
}
