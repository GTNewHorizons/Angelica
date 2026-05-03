package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList;
import com.gtnewhorizons.angelica.glsm.recording.ImmediateModeRecorder;
import org.lwjgl.opengl.GL11;

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
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        runnable.run();
        GLStateManager.glEndList();
        CompiledDisplayList compiled = DisplayListManager.getDisplayList(list);
        assertNotNull(compiled, "Display list should be compiled");
        return compiled;
    }
}
