package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.QuadConverter;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import net.minecraft.client.renderer.Tessellator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static org.joml.Math.clamp;
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

    private static final Tracy.ZoneId Z_SDL_STREAM_DRAW = Tracy.zoneId("sdlStreamDraw", Tracy.COLOR_FFP);

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

    // Tracy profiling counters
    public static long streamedBytes;
    public static long streamDraws;
    public static long orphanFallbacks;
    public static long streamContiguous;
    private static int lastStreamEndVertex = -1;
    private static int lastStreamDrawMode = -1;

    static {
        // Initial repack buffer: 64KB
        repackCapacity = 0x10000;
        repackBuffer = memAlloc(repackCapacity);
        repackAddress = memAddress0(repackBuffer);
    }

    private static void init() {
        if (initialized) return;
        initialized = true;

        persistentBuffer = PersistentStreamingBuffer.createOrNull(PersistentStreamingBuffer.DEFAULT_CAPACITY);
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

        final int effectiveFlags;
        if (GLSMConfig.expandVertexFormats) {
            effectiveFlags = VertexFlags.COLOR_BIT | VertexFlags.TEXTURE_BIT | VertexFlags.NORMAL_BIT | VertexFlags.BRIGHTNESS_BIT;
            if (effectiveFlags != flags) {
                final int[] rawBuffer = tess.rawBuffer;
                final int defaultColor;
                if (!tess.hasColor) {
                    final var c = GLStateManager.getColor();
                    defaultColor = ((int)(clamp(0f, 1f, c.getAlpha()) * 255) << 24) | ((int)(clamp(0f, 1f, c.getBlue()) * 255) << 16)
                        | ((int)(clamp(0f, 1f, c.getGreen()) * 255) << 8) | (int)(clamp(0f, 1f, c.getRed()) * 255);
                } else {
                    defaultColor = 0;
                }

                final int defaultBrightness;
                if (!tess.hasBrightness) {
                    defaultBrightness = ((int) GLSMConfig.lastBrightnessY << 16) | ((int) GLSMConfig.lastBrightnessX & 0xFFFF);
                } else {
                    defaultBrightness = 0;
                }

                final int defaultNormal;
                if (!tess.hasNormals) {
                    final var n = ShaderManager.getCurrentNormal();
                    defaultNormal = ((int)(clamp(-1f, 1f, n.z) * 127) << 16) | (((int)(clamp(-1f, 1f, n.y) * 127) & 0xFF) << 8) | ((int)(clamp(-1f, 1f, n.x) * 127) & 0xFF);
                } else {
                    defaultNormal = 0;
                }

                final int defaultTexU, defaultTexV;
                if (!tess.hasTexture) {
                    final var tc = ShaderManager.getCurrentTexCoord();
                    defaultTexU = Float.floatToRawIntBits(tc.x);
                    defaultTexV = Float.floatToRawIntBits(tc.y);
                } else {
                    defaultTexU = 0; defaultTexV = 0;
                }

                for (int i = 0; i < vertexCount; i++) {
                    final int base = i * 8;
                    if (!tess.hasTexture)    { rawBuffer[base + 3] = defaultTexU; rawBuffer[base + 4] = defaultTexV; }
                    if (!tess.hasColor)      { rawBuffer[base + 5] = defaultColor; }
                    if (!tess.hasNormals)    { rawBuffer[base + 6] = defaultNormal; }
                    if (!tess.hasBrightness) { rawBuffer[base + 7] = defaultBrightness; }
                }
            }
        } else {
            effectiveFlags = flags;
        }
        final VertexFormat format = DefaultVertexFormat.ALL_FORMATS[effectiveFlags];
        final int vertexSize = format.getVertexSize();

        final int requiredBytes = vertexCount * vertexSize;
        final boolean locked = GLStateManager.acquireDrawLock();
        try {
            ensureRepackCapacity(requiredBytes);

            final long writePtr = format.writeToBuffer0(repackAddress, tess.rawBuffer, tess.rawBufferIndex);
            repackBuffer.position(0);
            repackBuffer.limit((int)(writePtr - repackAddress));

            uploadAndDraw(repackBuffer, effectiveFlags, format, vertexSize, tess.drawMode, vertexCount);
        } finally {
            if (locked) GLStateManager.releaseDrawLock();
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
        uploadAndDraw(packedData, flags, format, vertexSize, drawMode, vertexCount);
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
            cachedDebugInfo = String.format("Stream: Persistent %s (%s free, %d forced) + %d orphan (%s)",
                formatBytes(persistentBuffer.getCapacity()), formatBytes(persistentBuffer.getRemaining()),
                persistentBuffer.getForcedReclaims(), orphanCount, formatBytes(orphanBytes));
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
        Tracy.beginZone(Z_SDL_STREAM_DRAW);
        final boolean locked = GLStateManager.acquireDrawLock();
        try {
            ensureVAO(flags, format);

            if (Tracy.ENABLED) {
                streamedBytes += packed.remaining();
                streamDraws++;
            }

            int firstVertex = -1;

            if (persistentBuffer != null) {
                firstVertex = persistentBuffer.upload(packed, vertexSize, drawMode == GL11.GL_QUADS ? 4 : 1);
            }

            final boolean fromRing = firstVertex >= 0;
            if (fromRing) {
                GLStateManager.glBindVertexArray(persistentVAOs[flags]);
            } else {
                if (Tracy.ENABLED && persistentBuffer != null) orphanFallbacks++;
                GLStateManager.glBindVertexArray(orphanVAOs[flags]);
                orphanBuffers[flags].upload(packed);
                firstVertex = 0;
            }

            if (Tracy.ENABLED) {
                if (fromRing && firstVertex == lastStreamEndVertex && drawMode == lastStreamDrawMode) streamContiguous++;
                lastStreamEndVertex = fromRing ? firstVertex + vertexCount : -1;
                lastStreamDrawMode = drawMode;
            }

            drawWithQuadConversion(drawMode, firstVertex, vertexCount);
            GLStateManager.glBindVertexArray(0);
        } finally {
            if (locked) GLStateManager.releaseDrawLock();
            Tracy.endZone();
        }
    }

    public static long ringWraps() {
        return persistentBuffer == null ? 0L : persistentBuffer.getWraps();
    }

    private static void drawWithQuadConversion(int drawMode, int firstVertex, int vertexCount) {
        if (drawMode == GL11.GL_QUADS) {
            QuadConverter.drawQuadsAsTriangles(firstVertex, vertexCount);
        } else {
            GLStateManager.glDrawArrays(drawMode, firstVertex, vertexCount);
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
        }

        if (persistentBuffer != null && persistentVAOs[flags] == 0) {
            persistentVAOs[flags] = GLStateManager.glGenVertexArrays();
            GLStateManager.glBindVertexArray(persistentVAOs[flags]);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, persistentBuffer.getBufferId());
            format.setupBufferState(0L);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
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
