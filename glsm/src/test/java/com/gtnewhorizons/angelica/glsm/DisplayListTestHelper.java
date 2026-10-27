package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList;
import com.gtnewhorizons.angelica.glsm.recording.ImmediateModeRecorder;
import net.minecraft.client.renderer.Tessellator;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Helper class for building test data for display list batching tests.
 * Allows creating quads and AccumulatedDraws without GL context.
 */
public final class DisplayListTestHelper {

    private DisplayListTestHelper() {}

    public static void createSimpleDraw() {
        final DirectTessellator recorder = ImmediateModeRecorder.getInternalTessellator();
        // Record a single quad using immediate mode
        recorder.startDrawing(GL11.GL_QUADS);
        recorder.addVertex(0, 0, 0);
        recorder.addVertex(1, 0, 0);
        recorder.addVertex(1, 1, 0);
        recorder.addVertex(0, 1, 0);
        DisplayListManager.addImmediateModeDraw(ImmediateModeRecorder.end());
    }

    public static void createTextureDraw() {
        final DirectTessellator recorder = ImmediateModeRecorder.getInternalTessellator();
        // Record a single quad using immediate mode
        recorder.startDrawing(GL11.GL_QUADS);
        recorder.setTextureUV(0, 0);
        recorder.addVertex(0, 0, 0);
        recorder.setTextureUV(1, 0);
        recorder.addVertex(1, 0, 0);
        recorder.setTextureUV(1, 1);
        recorder.addVertex(1, 1, 0);
        recorder.setTextureUV(0, 1);
        recorder.addVertex(0, 1, 0);
        DisplayListManager.addImmediateModeDraw(ImmediateModeRecorder.end());
    }

    public static void createColorDraw() {
        final DirectTessellator recorder = ImmediateModeRecorder.getInternalTessellator();
        // Record a single quad using immediate mode
        recorder.startDrawing(GL11.GL_QUADS);
        recorder.setColorRGBA(255, 255, 255, 255);
        recorder.addVertex(0, 0, 0);
        recorder.addVertex(1, 0, 0);
        recorder.addVertex(1, 1, 0);
        recorder.addVertex(0, 1, 0);
        DisplayListManager.addImmediateModeDraw(ImmediateModeRecorder.end());
    }

    public static CompiledDisplayList createDisplayList(Runnable runnable) {
        int list = GL11.glGenLists(1);
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        runnable.run();
        GLSMDraws.glEndList();
        CompiledDisplayList compiled = DisplayListManager.getDisplayList(list);
        assertNotNull(compiled, "Display list should be compiled");
        return compiled;
    }

    /** Calls {@code list} on an identity modelview and returns the resulting matrix. The caller's matrix is restored. */
    public static FloatBuffer modelviewAfterCall(int list) {
        GLSMMatrices.glPushMatrix();
        try {
            GLSMMatrices.glLoadIdentity();
            GLSMDraws.glCallList(list);
            return GlTestSupport.modelview();
        } finally {
            GLSMMatrices.glPopMatrix();
        }
    }

    public static void assertMatrixEquals(Matrix4f expected, FloatBuffer actual, String message) {
        final FloatBuffer expectedBuf = BufferUtils.createFloatBuffer(16);
        expected.get(expectedBuf);
        for (int i = 0; i < 16; i++) {
            assertEquals(expectedBuf.get(i), actual.get(i), 0.0001f, message + " (element " + i + ")");
        }
    }

    public static void assertModelviewAfterCall(int list, Matrix4f expected, String message) {
        assertMatrixEquals(expected, modelviewAfterCall(list), message);
    }

    /** Immediate-mode quad from the origin to (size, size) at z = 0. */
    public static void immediateQuad(float size) {
        GLSMDraws.glBegin(GL11.GL_QUADS);
        GLSMDraws.glVertex3f(0.0f, 0.0f, 0.0f);
        GLSMDraws.glVertex3f(size, 0.0f, 0.0f);
        GLSMDraws.glVertex3f(size, size, 0.0f);
        GLSMDraws.glVertex3f(0.0f, size, 0.0f);
        GLSMDraws.glEnd();
    }

    /** Textured quad written straight into the vanilla Tessellator's raw buffer and handed to the draw interceptor. */
    public static void rawQuad(float size) {
        final Tessellator tess = Tessellator.instance;
        final float[][] corners = { { 0.0f, 0.0f }, { size, 0.0f }, { size, size }, { 0.0f, size } };
        tess.rawBuffer = new int[4 * 8];
        for (int i = 0; i < 4; i++) {
            final int base = i * 8;
            tess.rawBuffer[base] = Float.floatToRawIntBits(corners[i][0]);
            tess.rawBuffer[base + 1] = Float.floatToRawIntBits(corners[i][1]);
            tess.rawBuffer[base + 3] = Float.floatToRawIntBits(i == 1 || i == 2 ? 1.0f : 0.0f);
            tess.rawBuffer[base + 4] = Float.floatToRawIntBits(i >= 2 ? 1.0f : 0.0f);
        }
        tess.isDrawing = true;
        tess.drawMode = GL11.GL_QUADS;
        tess.hasTexture = true;
        tess.rawBufferIndex = 4 * 8;
        tess.vertexCount = 4;
        TessellatorManager.interceptDraw(tess);
    }
}
