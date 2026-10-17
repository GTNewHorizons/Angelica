package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

public final class TessellatorEntityIdsFixture {

    public static final String VERTEX = """
        #version 330 core
        layout(location = 0) in vec3 a_Position;
        layout(location = %d) in vec4 iris_Entity;
        flat out vec3 v_Color;
        void main() {
            gl_Position = vec4(a_Position, 1.0);
            int item = int(round(iris_Entity.z * 65535.0));
            v_Color = item == 7 ? vec3(0.0, 1.0, 0.0) : item == 50020 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
        }
        """.formatted(ImmediateExtendedAttribHandler.LOC_ENTITY_IDS);

    public static final String FRAGMENT = """
        #version 330 core
        flat in vec3 v_Color;
        out vec4 fragColor;
        void main() { fragColor = vec4(v_Color, 1.0); }
        """;

    private TessellatorEntityIdsFixture() {}

    public static void drawIdsChangedMidBatch() {
        draw(() -> {
            GLStateManager.setEntityIds(-1, 0, 7);
            startQuads();
            addQuad(-1.0f, 0.0f);
            GLStateManager.setEntityIds(-1, 0, 50020);
            addQuad(0.0f, 1.0f);
            GLStateManager.setEntityIds(-1, 0, -1);
        });
    }

    public static void drawIdsChangedAfterTheLastVertex() {
        draw(() -> {
            GLStateManager.setEntityIds(-1, 0, 7);
            startQuads();
            addQuad(-1.0f, 1.0f);
            GLStateManager.setEntityIds(-1, 0, 50020);
        });
    }

    private static void draw(Runnable fill) {
        final Tessellator tess = Tessellator.instance;
        final int[] savedRawBuffer = tess.rawBuffer;
        final int savedRawBufferSize = tess.rawBufferSize;
        final int savedRawBufferIndex = tess.rawBufferIndex;
        final int savedVertexCount = tess.vertexCount;
        final int savedDrawMode = tess.drawMode;
        final boolean savedIsDrawing = tess.isDrawing;
        final boolean savedHasTexture = tess.hasTexture;
        final boolean savedHasColor = tess.hasColor;
        final boolean savedHasNormals = tess.hasNormals;
        final boolean savedHasBrightness = tess.hasBrightness;
        try {
            fill.run();
            TessellatorStreamingDrawer.draw(tess);
        } finally {
            TessellatorEntityIds.onReset(tess);
            GLStateManager.setEntityIds(-1, 0, -1);
            tess.rawBuffer = savedRawBuffer;
            tess.rawBufferSize = savedRawBufferSize;
            tess.rawBufferIndex = savedRawBufferIndex;
            tess.vertexCount = savedVertexCount;
            tess.drawMode = savedDrawMode;
            tess.isDrawing = savedIsDrawing;
            tess.hasTexture = savedHasTexture;
            tess.hasColor = savedHasColor;
            tess.hasNormals = savedHasNormals;
            tess.hasBrightness = savedHasBrightness;
        }
    }

    private static void startQuads() {
        final Tessellator tess = Tessellator.instance;
        tess.rawBuffer = new int[64];
        tess.rawBufferSize = tess.rawBuffer.length;
        tess.rawBufferIndex = 0;
        tess.vertexCount = 0;
        tess.drawMode = GL11.GL_QUADS;
        tess.hasTexture = false;
        tess.hasColor = false;
        tess.hasNormals = false;
        tess.hasBrightness = false;
        tess.isDrawing = true;
    }

    private static void addQuad(float left, float right) {
        addVertex(left, -1.0f);
        addVertex(right, -1.0f);
        addVertex(right, 1.0f);
        addVertex(left, 1.0f);
    }

    private static void addVertex(float x, float y) {
        final Tessellator tess = Tessellator.instance;
        final int base = tess.rawBufferIndex;
        tess.rawBuffer[base] = Float.floatToRawIntBits(x);
        tess.rawBuffer[base + 1] = Float.floatToRawIntBits(y);
        tess.rawBuffer[base + 2] = Float.floatToRawIntBits(0.0f);
        tess.rawBufferIndex += 8;
        tess.vertexCount++;
    }
}
