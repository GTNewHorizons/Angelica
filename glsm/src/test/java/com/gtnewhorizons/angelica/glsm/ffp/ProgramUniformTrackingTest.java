package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLSMCoreExtension;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(GLSMCoreExtension.class)
class ProgramUniformTrackingTest {

    private static Program buildProgram(boolean hasColor) {
        final VertexKey vk = VertexKey.fromState(hasColor, false, false, false, 0);
        final FragmentKey fk = FragmentKey.fromState();
        return Program.create(vk, fk, VertexShaderGenerator.generate(vk), FragmentShaderGenerator.generate(fk), null);
    }

    @Test
    void matrixTrackingIsPerProgram() {
        final Uniforms uniforms = new Uniforms();
        final Program a = buildProgram(true);
        final Program b = buildProgram(false);
        assertNotEquals(a.getProgramId(), b.getProgramId(), "distinct vertex keys must yield distinct programs");
        try {
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            GLStateManager.glPushMatrix();
            GLStateManager.glLoadIdentity();

            GLStateManager.glTranslatef(1.0F, 2.0F, 3.0F);
            final int genA = GLStateManager.mvGeneration;
            GL20.glUseProgram(a.getProgramId());
            uniforms.upload(a);
            assertEquals(genA, a.uploadState.mvGen, "A records the mv generation it uploaded");

            GLStateManager.glTranslatef(4.0F, 0.0F, 0.0F);
            final int genB = GLStateManager.mvGeneration;
            assertNotEquals(genA, genB);
            GL20.glUseProgram(b.getProgramId());
            uniforms.upload(b);
            assertEquals(genB, b.uploadState.mvGen, "B records the new mv generation");
            assertEquals(genA, a.uploadState.mvGen, "A is unchanged -- tracking is per-program, not global");

            // Return to A with the modelview still at genB: A is stale -> re-uploads and catches up.
            GL20.glUseProgram(a.getProgramId());
            uniforms.upload(a);
            assertEquals(genB, a.uploadState.mvGen, "A re-uploads on return and catches up to genB");

            // Re-upload A with no change: gate (mvGeneration == A.mvGen) is satisfied -> skip.
            uniforms.upload(a);
            assertEquals(genB, a.uploadState.mvGen, "A already current -> skipped");

            GLStateManager.glPopMatrix();
        } finally {
            GL20.glUseProgram(0);
            a.destroy();
            b.destroy();
        }
    }
}
