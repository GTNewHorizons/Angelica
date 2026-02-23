package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.QuadConverter;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;

/**
 * Replaces the vanilla Tessellator's FFP client-array draw path with a streaming VBO+VAO approach for GL 3.3 core profile compatibility.
 * <p>
 * Maintains a cached VAO+VBO pair per vertex format (up to 16 combinations). Data is repacked from vanilla's 32-byte/vertex stride into the optimal
 * VertexFormat, then stream-uploaded to the VBO and drawn via the VAO.
 */
public class TessellatorStreamingDrawer {

    private static final int FORMAT_COUNT = VertexFlags.BITSET_SIZE; // 16
    private static final int[] vboIds = new int[FORMAT_COUNT];
    private static final int[] vaoIds = new int[FORMAT_COUNT];
    private static final int[] vboCapacities = new int[FORMAT_COUNT]; // track allocated capacity

    private static ByteBuffer repackBuffer;
    private static long repackAddress;
    private static int repackCapacity;


    static {
        // Initial repack buffer: 64KB
        repackCapacity = 0x10000;
        repackBuffer = memAlloc(repackCapacity);
        repackAddress = memAddress0(repackBuffer);
    }

    /**
     * Draw the vanilla Tessellator's data using streaming VBO+VAO instead of FFP client arrays.
     */
    public static int draw(Tessellator tess) {
        if (!tess.isDrawing) {
            throw new IllegalStateException("Not tesselating!");
        }

        tess.isDrawing = false;

        final int vertexCount = tess.vertexCount;
        if (vertexCount == 0) {
            final int result = tess.rawBufferIndex * 4;
            tess.reset();
            return result;
        }

        // Determine the optimal vertex format from the tessellator's flags
        final int flags = VertexFlags.convertToFlags(tess.hasTexture, tess.hasColor, tess.hasNormals, tess.hasBrightness);
        final VertexFormat format = DefaultVertexFormat.ALL_FORMATS[flags];
        final int vertexSize = format.getVertexSize();

        final int requiredBytes = vertexCount * vertexSize;
        ensureRepackCapacity(requiredBytes);

        long writePtr = format.writeToBuffer0(repackAddress, tess.rawBuffer, tess.rawBufferIndex);
        repackBuffer.position(0);
        repackBuffer.limit((int)(writePtr - repackAddress));

        ensureVAO(flags, format);
        GLStateManager.glBindVertexArray(vaoIds[flags]);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboIds[flags]);
        streamUpload(flags, repackBuffer, requiredBytes);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        ShaderManager.getInstance().preDraw(flags);
        drawWithQuadConversion(tess.drawMode, vertexCount);

        GLStateManager.glBindVertexArray(0);

        // Shrink rawBuffer if oversized
        if (tess.rawBufferSize > 0x20000 && tess.rawBufferIndex < (tess.rawBufferSize << 3)) {
            tess.rawBufferSize = 0x10000;
            tess.rawBuffer = new int[tess.rawBufferSize];
        }

        final int result = tess.rawBufferIndex * 4;
        tess.reset();
        return result;
    }

    /**
     * Draw DirectTessellator data via streaming VBO+VAO. Used for live immediate mode emulation.
     */
    public static void drawDirect(DirectTessellator dt) {
        final VertexFormat format = dt.getVertexFormat();
        if (format == null) return;

        final int vertexCount = dt.getVertexCount();
        if (vertexCount == 0) return;

        final int drawMode = dt.getDrawMode();
        final int flags = format.getVertexFlags();

        // getWriteBuffer() returns the internal buffer directly — no allocation or copy.
        final ByteBuffer buffer = dt.getWriteBuffer();
        final int requiredBytes = buffer.remaining();

        ensureVAO(flags, format);
        GLStateManager.glBindVertexArray(vaoIds[flags]);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboIds[flags]);
        streamUpload(flags, buffer, requiredBytes);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        ShaderManager.getInstance().preDraw(flags);
        drawWithQuadConversion(drawMode, vertexCount);

        GLStateManager.glBindVertexArray(0);
    }

    private static void streamUpload(int flags, ByteBuffer data, int requiredBytes) {
        if (requiredBytes > vboCapacities[flags]) {
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
            vboCapacities[flags] = requiredBytes;
        } else {
            // Orphan + upload for streaming (avoids sync)
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vboCapacities[flags], GL15.GL_STREAM_DRAW);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, data);
        }
    }

    private static void drawWithQuadConversion(int drawMode, int vertexCount) {
        if (drawMode == GL11.GL_QUADS) {
            QuadConverter.drawQuadsAsTriangles(0, vertexCount);
        } else {
            GL11.glDrawArrays(drawMode, 0, vertexCount);
        }
    }

    private static void ensureRepackCapacity(int requiredBytes) {
        if (requiredBytes <= repackCapacity) return;

        int newCapacity = repackCapacity;
        while (newCapacity < requiredBytes) {
            newCapacity *= 2;
        }

        memFree(repackBuffer);
        repackBuffer = memAlloc(newCapacity);
        repackAddress = memAddress0(repackBuffer);
        repackCapacity = newCapacity;
    }

    private static void ensureVAO(int flags, VertexFormat format) {
        if (vaoIds[flags] != 0) return;

        // Create VBO
        vboIds[flags] = GL15.glGenBuffers();

        // Create VAO and configure vertex attributes
        vaoIds[flags] = GL30.glGenVertexArrays();
        GLStateManager.glBindVertexArray(vaoIds[flags]);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboIds[flags]);
        format.setupBufferState(0L);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        GLStateManager.glBindVertexArray(0);

        vboCapacities[flags] = 0;
    }

    /**
     * Clean up all VAOs, VBOs, and the repack buffer.
     */
    public static void destroy() {
        for (int i = 0; i < FORMAT_COUNT; i++) {
            if (vaoIds[i] != 0) { GLStateManager.glDeleteVertexArrays(vaoIds[i]); vaoIds[i] = 0; }
            if (vboIds[i] != 0) { GL15.glDeleteBuffers(vboIds[i]); vboIds[i] = 0; }
            vboCapacities[i] = 0;
        }
        if (repackBuffer != null) {
            memFree(repackBuffer);
            repackBuffer = null;
            repackAddress = 0;
            repackCapacity = 0;
        }
    }
}
