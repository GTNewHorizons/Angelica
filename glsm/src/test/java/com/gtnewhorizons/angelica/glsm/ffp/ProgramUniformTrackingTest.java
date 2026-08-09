package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
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
        return Program.create(vk, fk, VertexShaderGenerator.generate(vk), FragmentShaderGenerator.generate(fk), null);
    }

    @Test
    void programSwitchWritesNothingStateChangeWritesOnce() {
        final Uniforms uniforms = new Uniforms();
        final Program a = buildProgram(true);
        final Program b = buildProgram(false);
        assertNotEquals(a.getProgramId(), b.getProgramId(), "distinct vertex keys must yield distinct programs");
        try {
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            GLStateManager.glPushMatrix();
            GLStateManager.glLoadIdentity();
            GLStateManager.glTranslatef(1.0F, 2.0F, 3.0F);

            GL20.glUseProgram(a.getProgramId());
            uniforms.upload();
            final int afterFirst = uniforms.blockWrites;

            GL20.glUseProgram(b.getProgramId());
            uniforms.upload();
            GL20.glUseProgram(a.getProgramId());
            uniforms.upload();
            assertEquals(afterFirst, uniforms.blockWrites, "program switches with unchanged state write nothing");

            GLStateManager.glTranslatef(4.0F, 0.0F, 0.0F);
            uniforms.upload();
            assertEquals(afterFirst + 1, uniforms.blockWrites, "a gen bump writes exactly one block");

            uniforms.upload();
            assertEquals(afterFirst + 1, uniforms.blockWrites, "clean draw writes nothing");

            GLStateManager.glPopMatrix();
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
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            GLStateManager.glPushMatrix();
            GLStateManager.glLoadIdentity();

            GL20.glUseProgram(a.getProgramId());
            uniforms.upload();
            final int base = uniforms.blockWrites;

            GLStateManager.glPushAttrib(GL11.GL_TRANSFORM_BIT);
            GLStateManager.glPopAttrib();
            uniforms.upload();
            assertEquals(base, uniforms.blockWrites, "push/pop with no change inside writes nothing");

            GLStateManager.glPushAttrib(GL11.GL_TRANSFORM_BIT);
            GLStateManager.glTranslatef(7.0F, 0.0F, 0.0F);
            GLStateManager.glPopAttrib();
            uniforms.upload();
            assertEquals(base + 1, uniforms.blockWrites, "pop after change stages once");

            GLStateManager.glPopMatrix();
        } finally {
            GL20.glUseProgram(0);
            a.destroy();
            uniforms.destroy();
        }
    }
}
