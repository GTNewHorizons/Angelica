package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
class FFPUniformValuesTest {

    private static final float EPS = 1e-6f;

    @Test
    void uploadedValuesMatchState() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glTranslatef(1.0f, 2.0f, 3.0f);

        GLStateManager.glColor4f(0.25f, 0.5f, 0.75f, 1.0f);

        GLStateManager.enableFog();
        GLStateManager.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
        GLStateManager.glFogf(GL11.GL_FOG_START, 10.0f);
        GLStateManager.glFogf(GL11.GL_FOG_END, 20.0f);
        final FloatBuffer fogColor = BufferUtils.createFloatBuffer(4);
        fogColor.put(0.1f).put(0.2f).put(0.3f).put(0.4f).flip();
        GLStateManager.glFog(GL11.GL_FOG_COLOR, fogColor);

        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.35f);

        final VertexKey vk = VertexKey.fromState(false, false, false, false, 0);
        final FragmentKey fk = FragmentKey.fromState();
        final Program program = Program.create(vk, fk, VertexShaderGenerator.generate(vk), FragmentShaderGenerator.generate(fk), null);
        final Uniforms uniforms = new Uniforms();
        try {
            GL20.glUseProgram(program.getProgramId());
            uniforms.upload();

            final ByteBuffer block = uniforms.getStaging();

            assertEquals(1.0f, block.getFloat(FFPUniformBlock.MODEL_VIEW_MATRIX + 12 * 4), EPS, "modelView tx");
            assertEquals(2.0f, block.getFloat(FFPUniformBlock.MODEL_VIEW_MATRIX + 13 * 4), EPS, "modelView ty");
            assertEquals(3.0f, block.getFloat(FFPUniformBlock.MODEL_VIEW_MATRIX + 14 * 4), EPS, "modelView tz");

            assertEquals(1.0f, block.getFloat(FFPUniformBlock.MVP_MATRIX + 12 * 4), EPS, "mvp tx");
            assertEquals(2.0f, block.getFloat(FFPUniformBlock.MVP_MATRIX + 13 * 4), EPS, "mvp ty");
            assertEquals(3.0f, block.getFloat(FFPUniformBlock.MVP_MATRIX + 14 * 4), EPS, "mvp tz");

            assertEquals(0.25f, block.getFloat(FFPUniformBlock.CURRENT_COLOR), EPS, "color r");
            assertEquals(0.5f, block.getFloat(FFPUniformBlock.CURRENT_COLOR + 4), EPS, "color g");
            assertEquals(0.75f, block.getFloat(FFPUniformBlock.CURRENT_COLOR + 8), EPS, "color b");
            assertEquals(1.0f, block.getFloat(FFPUniformBlock.CURRENT_COLOR + 12), EPS, "color a");

            assertEquals(0.35f, block.getFloat(FFPUniformBlock.ALPHA_REF), EPS, "alpha ref");

            assertEquals(-0.1f, block.getFloat(FFPUniformBlock.FOG_PARAMS), EPS, "fog -1/range");
            assertEquals(2.0f, block.getFloat(FFPUniformBlock.FOG_PARAMS + 4), EPS, "fog end/range");

            assertEquals(0.1f, block.getFloat(FFPUniformBlock.FOG_COLOR), EPS, "fog color r");
            assertEquals(0.2f, block.getFloat(FFPUniformBlock.FOG_COLOR + 4), EPS, "fog color g");
            assertEquals(0.3f, block.getFloat(FFPUniformBlock.FOG_COLOR + 8), EPS, "fog color b");
            assertEquals(0.4f, block.getFloat(FFPUniformBlock.FOG_COLOR + 12), EPS, "fog color a");
        } finally {
            GL20.glUseProgram(0);
            program.destroy();
            uniforms.destroy();

            GLStateManager.disableAlphaTest();
            GLStateManager.glAlphaFunc(GL11.GL_ALWAYS, 0.0f);
            GLStateManager.disableFog();
            GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GLStateManager.glPopMatrix();
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            GLStateManager.glPopMatrix();
        }
    }
}
