package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.QuadConverter;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.streaming.OrphanStreamingBuffer;
import com.gtnewhorizons.angelica.glsm.streaming.PersistentStreamingBuffer;
import net.minecraft.client.renderer.Tessellator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
 * Uses a persistent-mapped ring buffer on GL4.4+ hardware (zero-copy uploads with fence sync),
 * falling back to the classic orphan pattern on older hardware or on overflow.
 * Maintains two VAO sets (persistent + orphan) per vertex format (up to 16 combinations).
 */
public class TessellatorStreamingDrawer {

    private static final Logger LOGGER = LogManager.getLogger("TessellatorStreamingDrawer");
    private static final int FORMAT_COUNT = VertexFlags.BITSET_SIZE; // 16

    private static PersistentStreamingBuffer persistentBuffer;
    private static final OrphanStreamingBuffer[] orphanBuffers = new OrphanStreamingBuffer[FORMAT_COUNT];

    private static final int[] persistentVAOs = new int[FORMAT_COUNT];
    private static final int[] orphanVAOs = new int[FORMAT_COUNT];

    private static ByteBuffer repackBuffer;
    private static long repackAddress;
    private static int repackCapacity;

    private static boolean initialized = false;

    static {
        // Initial repack buffer: 64KB
        repackCapacity = 0x10000;
        repackBuffer = memAlloc(repackCapacity);
        repackAddress = memAddress0(repackBuffer);
    }

    private static void init() {
        if (initialized) return;
        initialized = true;
        if (RenderSystem.supportsBufferStorage()) {
            try {
                persistentBuffer = new PersistentStreamingBuffer();
                LOGGER.info("Persistent streaming buffer created ({}MB)", PersistentStreamingBuffer.DEFAULT_CAPACITY / (1024 * 1024));
            } catch (Exception e) {
                LOGGER.warn("Failed to create persistent streaming buffer, using orphan fallback", e);
                persistentBuffer = null;
            }
        }
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

        final long writePtr = format.writeToBuffer0(repackAddress, tess.rawBuffer, tess.rawBufferIndex);
        repackBuffer.position(0);
        repackBuffer.limit((int)(writePtr - repackAddress));

        ensureVAO(flags, format);

        int firstVertex = -1;

        if (persistentBuffer != null) {
            firstVertex = persistentBuffer.upload(repackBuffer, vertexSize);
        }

        if (firstVertex >= 0) {
            GLStateManager.glBindVertexArray(persistentVAOs[flags]);
        } else {
            GLStateManager.glBindVertexArray(orphanVAOs[flags]);
            orphanBuffers[flags].upload(repackBuffer, vertexSize);
            firstVertex = 0;
        }

        ShaderManager.getInstance().preDraw(flags);
        drawWithQuadConversion(tess.drawMode, firstVertex, vertexCount);

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
        final ByteBuffer buffer = dt.getWriteBuffer();
        final int vertexSize = format.getVertexSize();

        ensureVAO(flags, format);

        int firstVertex = -1;

        if (persistentBuffer != null) {
            firstVertex = persistentBuffer.upload(buffer, vertexSize);
        }

        if (firstVertex >= 0) {
            GLStateManager.glBindVertexArray(persistentVAOs[flags]);
        } else {
            GLStateManager.glBindVertexArray(orphanVAOs[flags]);
            orphanBuffers[flags].upload(buffer, vertexSize);
            firstVertex = 0;
        }

        ShaderManager.getInstance().preDraw(flags);
        drawWithQuadConversion(drawMode, firstVertex, vertexCount);

        GLStateManager.glBindVertexArray(0);
    }

    public static String getDebugInfo() {
        if (!initialized) return "Stream: not initialized";

        int orphanCount = 0;
        int orphanBytes = 0;
        for (int i = 0; i < FORMAT_COUNT; i++) {
            if (orphanBuffers[i] != null) {
                orphanCount++;
                orphanBytes += orphanBuffers[i].getCapacity();
            }
        }

        if (persistentBuffer != null) {
            return String.format("Stream: Persistent %s (%s free) + %d orphan (%s)",
                formatBytes(persistentBuffer.getCapacity()), formatBytes(persistentBuffer.getRemaining()),
                orphanCount, formatBytes(orphanBytes));
        }
        return String.format("Stream: Orphan (%d bufs, %s)", orphanCount, formatBytes(orphanBytes));
    }

    private static String formatBytes(int bytes) {
        if (bytes >= 1024 * 1024) return String.format("%dMB", bytes / (1024 * 1024));
        if (bytes >= 1024) return String.format("%dKB", bytes / 1024);
        return bytes + "B";
    }

    public static void endFrame() {
        if (persistentBuffer != null) {
            persistentBuffer.postDraw();
        }
    }

    private static void drawWithQuadConversion(int drawMode, int firstVertex, int vertexCount) {
        if (drawMode == GL11.GL_QUADS) {
            QuadConverter.drawQuadsAsTriangles(firstVertex, vertexCount);
        } else {
            GL11.glDrawArrays(drawMode, firstVertex, vertexCount);
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
        init();

        if (orphanVAOs[flags] == 0) {
            orphanBuffers[flags] = new OrphanStreamingBuffer();

            orphanVAOs[flags] = GL30.glGenVertexArrays();
            GLStateManager.glBindVertexArray(orphanVAOs[flags]);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, orphanBuffers[flags].getBufferId());
            format.setupBufferState(0L);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glBindVertexArray(0);
        }

        if (persistentBuffer != null && persistentVAOs[flags] == 0) {
            persistentVAOs[flags] = GL30.glGenVertexArrays();
            GLStateManager.glBindVertexArray(persistentVAOs[flags]);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, persistentBuffer.getBufferId());
            format.setupBufferState(0L);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glBindVertexArray(0);
        }
    }

    /**
     * Clean up all VAOs, streaming buffers, and the repack buffer.
     */
    public static void destroy() {
        for (int i = 0; i < FORMAT_COUNT; i++) {
            if (persistentVAOs[i] != 0) { GLStateManager.glDeleteVertexArrays(persistentVAOs[i]); persistentVAOs[i] = 0; }
            if (orphanVAOs[i] != 0) { GLStateManager.glDeleteVertexArrays(orphanVAOs[i]); orphanVAOs[i] = 0; }
            if (orphanBuffers[i] != null) { orphanBuffers[i].destroy(); orphanBuffers[i] = null; }
        }
        if (persistentBuffer != null) {
            persistentBuffer.destroy();
            persistentBuffer = null;
        }
        if (repackBuffer != null) {
            memFree(repackBuffer);
            repackBuffer = null;
            repackAddress = 0;
            repackCapacity = 0;
        }
        initialized = false;
    }
}
