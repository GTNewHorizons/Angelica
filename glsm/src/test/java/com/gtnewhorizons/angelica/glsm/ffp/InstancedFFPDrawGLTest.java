package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags.COLOR_BIT;
import static com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags.NORMAL_BIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class InstancedFFPDrawGLTest {

    private static final int TEMPLATE_STRIDE = 32;
    private static final int INSTANCE_STRIDE = InstancedAttribs.STRIDE;

    private int vao, templateVbo, instanceVbo;

    @AfterEach
    void cleanup() {
        GLStateManager.instancedFfpDrawActive = false;
        final ShaderManager sm = ShaderManager.getInstance();
        if (sm.isActive()) sm.deactivate();
        sm.disable();
        GLStateManager.glBindVertexArray(0);
        if (templateVbo != 0) { GLStateManager.glDeleteBuffers(templateVbo); templateVbo = 0; }
        if (instanceVbo != 0) { GLStateManager.glDeleteBuffers(instanceVbo); instanceVbo = 0; }
        if (vao != 0) { GLStateManager.glDeleteVertexArrays(vao); vao = 0; }
    }

    @Test
    void vertexKeyCarriesInstancedBit() {
        GLStateManager.instancedFfpDrawActive = false;
        final long plain = VertexKey.packFromState(true, true, false, false, 0);
        GLStateManager.instancedFfpDrawActive = true;
        final long instanced = VertexKey.packFromState(true, true, false, false, 0);
        GLStateManager.instancedFfpDrawActive = false;

        assertFalse(VertexKey.fromPacked(plain).instancedDraw());
        assertTrue(VertexKey.fromPacked(instanced).instancedDraw());
        assertEquals(1, Long.bitCount(plain ^ instanced), "only the instanced bit may differ");
    }

    @Test
    void instancedSourceUsesInstanceAttribs() {
        GLStateManager.instancedFfpDrawActive = true;
        final VertexKey key = VertexKey.fromState(true, true, false, false, 0b10);
        GLStateManager.instancedFfpDrawActive = false;

        final String source = VertexShaderGenerator.generate(key);
        assertTrue(source.contains("a_InstCol0"), "instance matrix attribs declared");
        assertTrue(source.contains("mat4 instMV"), "instance matrix used as modelview");
        assertTrue(source.contains("a_InstColor"), "instance color multiplier used");
        assertTrue(source.contains("a_InstLightmap"), "instance lightmap coord used");
        assertFalse(source.contains("u_MVPMatrix * pos4"), "uniform MVP transform unused in instanced variant");
    }

    @Test
    void twoInstancesDrawWithDistinctTransformsAndColors() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glViewport(0, 0, 800, 600);
        GLStateManager.disableDepthTest();
        GLStateManager.disableCull();

        buildTemplate();
        buildInstances();

        final ShaderManager sm = ShaderManager.getInstance();
        sm.enable();
        sm.activate();

        GLStateManager.glClearColor(0f, 0f, 0f, 1f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GLStateManager.instancedFfpDrawActive = true;
        GLStateManager.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 3, 2);
        GLStateManager.instancedFfpDrawActive = false;

        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "instanced draw must not raise a GL error");

        final int[] left = readPixel(200, 300);
        final int[] right = readPixel(600, 300);
        assertTrue(left[0] > 200 && left[1] < 50, "left instance red, got " + left[0] + "," + left[1] + "," + left[2]);
        assertTrue(right[1] > 200 && right[0] < 50, "right instance green, got " + right[0] + "," + right[1] + "," + right[2]);
    }

    @Test
    void instancedAlphaTestDiscardsLowAlphaInstance() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glViewport(0, 0, 800, 600);
        GLStateManager.disableDepthTest();
        GLStateManager.disableCull();

        buildTemplate();
        buildInstancesWithAlpha();

        final ShaderManager sm = ShaderManager.getInstance();
        sm.enable();
        sm.activate();
        GLStateManager.enableAlphaTest();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1f);

        GLStateManager.glClearColor(0f, 0f, 0f, 1f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GLStateManager.instancedFfpDrawActive = true;
        GLStateManager.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 3, 2);
        GLStateManager.instancedFfpDrawActive = false;
        GLStateManager.disableAlphaTest();

        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        final int[] left = readPixel(200, 300);
        final int[] right = readPixel(600, 300);
        assertTrue(left[0] > 200, "opaque instance drawn, got " + left[0]);
        assertTrue(right[0] < 20 && right[1] < 20, "alpha 0.05 instance discarded, got " + right[0] + "," + right[1]);
    }

    @Test
    void equalDepthOverlayBlendsOnlyOverBase() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glViewport(0, 0, 800, 600);
        GLStateManager.enableDepthTest();
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.disableCull();

        buildTemplate();
        buildInstances();

        final ShaderManager sm = ShaderManager.getInstance();
        sm.enable();
        sm.activate();

        GLStateManager.glClearColor(0f, 0f, 0f, 1f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        GLStateManager.instancedFfpDrawActive = true;
        GLStateManager.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 3, 2);

        GLStateManager.glDepthFunc(GL11.GL_EQUAL);
        GLStateManager.enableBlend();
        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GLStateManager.glColor4f(1f, 1f, 1f, 1f);
        rewriteInstanceColors(0x800000FF, 0x8000FF00);
        GLStateManager.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 3, 2);
        GLStateManager.instancedFfpDrawActive = false;

        GLStateManager.disableBlend();
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.disableDepthTest();

        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        final int[] left = readPixel(200, 300);
        final int[] background = readPixel(400, 550);
        assertTrue(left[0] > 200, "overlay blended over base, got " + left[0]);
        assertTrue(background[0] < 20 && background[1] < 20, "overlay must not draw off-base, got " + background[0]);
    }

    private void rewriteInstanceColors(int leftABGR, int rightABGR) {
        final ByteBuffer patch = BufferUtils.createByteBuffer(2 * INSTANCE_STRIDE);
        putInstance(patch, -0.5f, leftABGR);
        putInstance(patch, 0.5f, rightABGR);
        patch.flip();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, patch, GL15.GL_STATIC_DRAW);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private void buildInstancesWithAlpha() {
        final ByteBuffer instances = BufferUtils.createByteBuffer(2 * INSTANCE_STRIDE);
        putInstance(instances, -0.5f, 0xFF0000FF);
        putInstance(instances, 0.5f, 0x0D0000FF);
        instances.flip();

        instanceVbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, instances, GL15.GL_STATIC_DRAW);
        pointAndEnableInstanceAttribs();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private void buildTemplate() {
        vao = GLStateManager.glGenVertexArrays();
        GLStateManager.glBindVertexArray(vao);

        final ByteBuffer template = BufferUtils.createByteBuffer(3 * TEMPLATE_STRIDE);
        putVertex(template, -0.4f, -0.4f);
        putVertex(template, 0.4f, -0.4f);
        putVertex(template, 0.0f, 0.4f);
        template.flip();

        templateVbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, templateVbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, template, GL15.GL_STATIC_DRAW);
        GLStateManager.glEnableVertexAttribArray(0);
        GLStateManager.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, TEMPLATE_STRIDE, 0L);
        GLStateManager.glEnableVertexAttribArray(1);
        GLStateManager.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, TEMPLATE_STRIDE, 20L);
        GLStateManager.glEnableVertexAttribArray(4);
        GLStateManager.glVertexAttribPointer(4, 3, GL11.GL_BYTE, true, TEMPLATE_STRIDE, 24L);
        VAOManager.setCurrentVertexFlags(COLOR_BIT | NORMAL_BIT);
    }

    private void putVertex(ByteBuffer buf, float x, float y) {
        final int base = buf.position();
        buf.putFloat(base, x).putFloat(base + 4, y).putFloat(base + 8, 0f);
        buf.putInt(base + 20, 0xFFFFFFFF);
        buf.putInt(base + 24, 0x00007F00);
        buf.position(base + TEMPLATE_STRIDE);
    }

    private void buildInstances() {
        final ByteBuffer instances = BufferUtils.createByteBuffer(2 * INSTANCE_STRIDE);
        putInstance(instances, -0.5f, 0xFF0000FF);
        putInstance(instances, 0.5f, 0xFF00FF00);
        instances.flip();

        instanceVbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, instances, GL15.GL_STATIC_DRAW);
        pointAndEnableInstanceAttribs();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private static void pointAndEnableInstanceAttribs() {
        for (int c = 0; c < 4; c++) {
            final int loc = InstancedAttribs.LOC_MATRIX_COL0 + c;
            GLStateManager.glEnableVertexAttribArray(loc);
            GLStateManager.glVertexAttribPointer(loc, 4, GL11.GL_FLOAT, false, INSTANCE_STRIDE, c * 16L);
            GLStateManager.glVertexAttribDivisor(loc, 1);
        }
        GLStateManager.glEnableVertexAttribArray(InstancedAttribs.LOC_COLOR);
        GLStateManager.glVertexAttribPointer(InstancedAttribs.LOC_COLOR, 4, GL11.GL_UNSIGNED_BYTE, true, INSTANCE_STRIDE, InstancedAttribs.OFFSET_COLOR);
        GLStateManager.glVertexAttribDivisor(InstancedAttribs.LOC_COLOR, 1);
        GLStateManager.glEnableVertexAttribArray(InstancedAttribs.LOC_LIGHTMAP);
        GLStateManager.glVertexAttribPointer(InstancedAttribs.LOC_LIGHTMAP, 2, GL11.GL_FLOAT, false, INSTANCE_STRIDE, InstancedAttribs.OFFSET_LIGHTMAP);
        GLStateManager.glVertexAttribDivisor(InstancedAttribs.LOC_LIGHTMAP, 1);
    }

    private void putInstance(ByteBuffer buf, float translateX, int colorABGR) {
        final int base = buf.position();
        buf.putFloat(base, 1f).putFloat(base + 20, 1f).putFloat(base + 40, 1f).putFloat(base + 60, 1f);
        buf.putFloat(base + 48, translateX);
        buf.putInt(base + InstancedAttribs.OFFSET_COLOR, colorABGR);
        buf.putFloat(base + InstancedAttribs.OFFSET_LIGHTMAP, 240f);
        buf.putFloat(base + InstancedAttribs.OFFSET_LIGHTMAP + 4, 240f);
        buf.position(base + INSTANCE_STRIDE);
    }

    private static int[] readPixel(int x, int y) {
        final ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        return new int[] { pixel.get(0) & 0xFF, pixel.get(1) & 0xFF, pixel.get(2) & 0xFF };
    }
}
