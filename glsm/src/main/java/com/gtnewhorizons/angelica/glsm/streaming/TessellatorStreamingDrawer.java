package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.QuadConverter;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import net.minecraft.client.renderer.Tessellator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCopy;
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

    private static final int[] extendedPersistentVAOs = new int[FORMAT_COUNT];
    private static final int[] extendedOrphanVAOs = new int[FORMAT_COUNT];
    private static ByteBuffer extScratch;
    private static long extScratchAddress;
    private static int extScratchCapacity;

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

        if (RenderSystem.supportsBufferStorage() && !Boolean.getBoolean("angelica.forceOrphanStreaming")) {
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

        final ImmediateExtendedAttribHandler extHandler = GLSMHooks.immediateExtendedHandler;
        if (extHandler != null && tess.drawMode == GL11.GL_QUADS && tess.hasTexture && (vertexCount & 3) == 0
                && extHandler.wantsExtended()) {
            final int combinedStride = vertexSize + ImmediateExtendedAttribHandler.EXT_STRIDE;
            final int combinedBytes = vertexCount * combinedStride;
            ensureExtScratch(combinedBytes);
            interleaveBase(repackAddress, extScratchAddress, vertexCount, vertexSize, combinedStride);
            extHandler.build(tess.rawBuffer, vertexCount, extScratchAddress + vertexSize, combinedStride);
            extScratch.position(0);
            extScratch.limit(combinedBytes);
            uploadAndDrawExtended(extScratch, flags, format, combinedStride, vertexCount);
            shrinkExtScratchIfOversized(combinedBytes);
        } else {
            uploadAndDraw(repackBuffer, flags, format, vertexSize, tess.drawMode, vertexCount);
        }

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

        if (tryExtendedPacked(buffer, flags, format, vertexSize, drawMode, vertexCount)) return;
        uploadAndDraw(buffer, flags, format, vertexSize, drawMode, vertexCount);
    }

    /**
     * Upload pre-packed vertex data and draw. Public API for external batch systems.
     * @param packedData  buffer positioned at 0 with limit set to total bytes
     * @param drawMode    GL draw mode (GL_QUADS, GL_TRIANGLES, etc.)
     * @param flags       vertex format flags (from VertexFlags)
     * @param vertexCount number of vertices
     */
    public static void drawPacked(ByteBuffer packedData, int drawMode, int flags, int vertexCount) {
        final VertexFormat format = DefaultVertexFormat.ALL_FORMATS[flags];
        final int vertexSize = format.getVertexSize();
        if (tryExtendedPacked(packedData, flags, format, vertexSize, drawMode, vertexCount)) return;
        uploadAndDraw(packedData, flags, format, vertexSize, drawMode, vertexCount);
    }

    private static boolean tryExtendedPacked(ByteBuffer packed, int flags, VertexFormat format, int vertexSize, int drawMode, int vertexCount) {
        final ImmediateExtendedAttribHandler extHandler = GLSMHooks.immediateExtendedHandler;
        if (extHandler == null || !extHandler.wantsExtended()) return false;
        if (drawMode != GL11.GL_QUADS || !format.hasTexture() || (vertexCount & 3) != 0) return false;

        final long srcBase = memAddress0(packed) + packed.position();
        final int texOffset = ImmediateExtendedAttribHandler.texOffset(format);
        final int combinedStride = vertexSize + ImmediateExtendedAttribHandler.EXT_STRIDE;
        final int combinedBytes = vertexCount * combinedStride;
        ensureExtScratch(combinedBytes);
        interleaveBase(srcBase, extScratchAddress, vertexCount, vertexSize, combinedStride);
        extHandler.buildPacked(srcBase, vertexSize, 0, texOffset, vertexCount, extScratchAddress + vertexSize, combinedStride);
        extScratch.position(0);
        extScratch.limit(combinedBytes);
        uploadAndDrawExtended(extScratch, flags, format, combinedStride, vertexCount);
        shrinkExtScratchIfOversized(combinedBytes);
        return true;
    }

    private static void interleaveBase(long src, long dst, int vertexCount, int vertexSize, int combinedStride) {
        for (int i = 0; i < vertexCount; i++) {
            memCopy(src + (long) i * vertexSize, dst + (long) i * combinedStride, vertexSize);
        }
    }

    private static String cachedDebugInfo = "Stream: not initialized";
    private static long lastDebugUpdateNanos;
    private static final long DEBUG_UPDATE_INTERVAL_NS = 500_000_000L; // 500ms

    public static String getDebugInfo() {
        if (!initialized) return "Stream: not initialized";

        final long now = System.nanoTime();
        if (now - lastDebugUpdateNanos < DEBUG_UPDATE_INTERVAL_NS) {
            return cachedDebugInfo;
        }
        lastDebugUpdateNanos = now;

        int orphanCount = 0;
        int orphanBytes = 0;
        for (int i = 0; i < FORMAT_COUNT; i++) {
            if (orphanBuffers[i] != null) {
                orphanCount++;
                orphanBytes += orphanBuffers[i].getCapacity();
            }
        }

        if (persistentBuffer != null) {
            cachedDebugInfo = String.format("Stream: Persistent %s (%s free) + %d orphan (%s)",
                formatBytes(persistentBuffer.getCapacity()), formatBytes(persistentBuffer.getRemaining()),
                orphanCount, formatBytes(orphanBytes));
        } else {
            cachedDebugInfo = String.format("Stream: Orphan (%d bufs, %s)", orphanCount, formatBytes(orphanBytes));
        }
        return cachedDebugInfo;
    }

    private static String formatBytes(int bytes) {
        if (bytes >= 1024 * 1024) return String.format("%5.1fMB", bytes / (1024.0 * 1024.0));
        if (bytes >= 1024) return String.format("%5.1fKB", bytes / 1024.0);
        return String.format("%5dB", bytes);
    }

    public static void endFrame() {
        if (persistentBuffer != null) {
            persistentBuffer.postDraw();
        }
    }

    /**
     * Upload packed vertex data to a streaming buffer and issue the draw call.
     * Tries the persistent ring buffer first, falls back to orphan buffer on overflow.
     */
    private static void uploadAndDraw(ByteBuffer packed, int flags, VertexFormat format, int vertexSize, int drawMode, int vertexCount) {
        ensureVAO(flags, format);

        int firstVertex = -1;

        if (persistentBuffer != null) {
            firstVertex = persistentBuffer.upload(packed, vertexSize);
        }

        if (firstVertex >= 0) {
            GLStateManager.glBindVertexArray(persistentVAOs[flags]);
        } else {
            GLStateManager.glBindVertexArray(orphanVAOs[flags]);
            orphanBuffers[flags].upload(packed);
            firstVertex = 0;
        }

        drawWithQuadConversion(drawMode, firstVertex, vertexCount);
        GLStateManager.glBindVertexArray(0);
    }

    private static void drawWithQuadConversion(int drawMode, int firstVertex, int vertexCount) {
        if (drawMode == GL11.GL_QUADS) {
            QuadConverter.drawQuadsAsTriangles(firstVertex, vertexCount);
        } else {
            GLStateManager.glDrawArrays(drawMode, firstVertex, vertexCount);
        }
    }

    private static void uploadAndDrawExtended(ByteBuffer combined, int flags, VertexFormat format, int combinedStride, int vertexCount) {
        ensureVAO(flags, format);
        ensureExtendedVAOs(flags, format);

        int firstVertex = -1;

        if (persistentBuffer != null) {
            firstVertex = persistentBuffer.upload(combined, combinedStride);
        }

        if (firstVertex >= 0) {
            GLStateManager.glBindVertexArray(extendedPersistentVAOs[flags]);
        } else {
            GLStateManager.glBindVertexArray(extendedOrphanVAOs[flags]);
            orphanBuffers[flags].upload(combined);
            firstVertex = 0;
        }

        QuadConverter.drawQuadsAsTriangles(firstVertex, vertexCount);
        GLStateManager.glBindVertexArray(0);
    }

    private static void ensureExtendedVAOs(int flags, VertexFormat format) {
        if (extendedOrphanVAOs[flags] == 0) {
            extendedOrphanVAOs[flags] = GLStateManager.glGenVertexArrays();
            GLStateManager.glBindVertexArray(extendedOrphanVAOs[flags]);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, orphanBuffers[flags].getBufferId());
            setupExtendedBufferState(format);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glBindVertexArray(0);
        }

        if (persistentBuffer != null && extendedPersistentVAOs[flags] == 0) {
            extendedPersistentVAOs[flags] = GLStateManager.glGenVertexArrays();
            GLStateManager.glBindVertexArray(extendedPersistentVAOs[flags]);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, persistentBuffer.getBufferId());
            setupExtendedBufferState(format);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glBindVertexArray(0);
        }
    }

    private static void setupExtendedBufferState(VertexFormat format) {
        final int combinedStride = format.getVertexSize() + ImmediateExtendedAttribHandler.EXT_STRIDE;
        long offset = 0;
        for (final VertexFormatElement element : format.elementsArray) {
            element.setupBufferState(offset, combinedStride);
            offset += element.getByteSize();
        }
        ImmediateExtendedAttribHandler.setupExtAttribPointers(offset, combinedStride);
    }

    private static void ensureExtScratch(int requiredBytes) {
        if (extScratch != null && requiredBytes <= extScratchCapacity) return;

        int newCapacity = Math.max(0x4000, extScratchCapacity);
        while (newCapacity < requiredBytes) {
            newCapacity *= 2;
        }
        if (extScratch != null) memFree(extScratch);
        extScratch = memAlloc(newCapacity);
        extScratchAddress = memAddress0(extScratch);
        extScratchCapacity = newCapacity;
    }

    private static void shrinkExtScratchIfOversized(int lastRequiredBytes) {
        if (extScratchCapacity > 0x20000 && lastRequiredBytes < (extScratchCapacity >> 3)) {
            memFree(extScratch);
            extScratchCapacity = 0x4000;
            extScratch = memAlloc(extScratchCapacity);
            extScratchAddress = memAddress0(extScratch);
        }
    }

    /**
     * Ensure the repack buffer is large enough for the given byte count.
     * Public for use by external batch systems that need to pack data before calling {@link #drawPacked}.
     */
    public static void ensureRepackCapacity(int requiredBytes) {
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

    /** Get the repack buffer's native address. Valid until next {@link #ensureRepackCapacity} call. */
    public static long getRepackAddress() {
        return repackAddress;
    }

    /** Get the repack ByteBuffer. Caller must set position/limit before passing to {@link #drawPacked}. */
    public static ByteBuffer getRepackBuffer() {
        return repackBuffer;
    }

    private static void ensureVAO(int flags, VertexFormat format) {
        init();

        if (orphanVAOs[flags] == 0) {
            orphanBuffers[flags] = new OrphanStreamingBuffer();

            orphanVAOs[flags] = GLStateManager.glGenVertexArrays();
            GLStateManager.glBindVertexArray(orphanVAOs[flags]);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, orphanBuffers[flags].getBufferId());
            format.setupBufferState(0L);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glBindVertexArray(0);
        }

        if (persistentBuffer != null && persistentVAOs[flags] == 0) {
            persistentVAOs[flags] = GLStateManager.glGenVertexArrays();
            GLStateManager.glBindVertexArray(persistentVAOs[flags]);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, persistentBuffer.getBufferId());
            format.setupBufferState(0L);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
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
            if (extendedPersistentVAOs[i] != 0) { GLStateManager.glDeleteVertexArrays(extendedPersistentVAOs[i]); extendedPersistentVAOs[i] = 0; }
            if (extendedOrphanVAOs[i] != 0) { GLStateManager.glDeleteVertexArrays(extendedOrphanVAOs[i]); extendedOrphanVAOs[i] = 0; }
            if (orphanBuffers[i] != null) { orphanBuffers[i].destroy(); orphanBuffers[i] = null; }
        }
        if (extScratch != null) {
            memFree(extScratch);
            extScratch = null;
            extScratchAddress = 0;
            extScratchCapacity = 0;
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
