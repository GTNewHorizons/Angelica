package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import lombok.Getter;
import org.lwjgl.opengl.GL11;

import java.util.Comparator;
import java.util.List;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;

/**
 * Deferred draw batching coordinator. During rendering regions (e.g. particles),
 * intercepts tessellator.draw() calls via DirectTessellator, captures pre-packed vertex data
 * + GL state, then flushes all captured batches sorted by state key.
 * <p>
 * Vertices are pre-transformed at capture time.
 */
public class DeferredDrawBatcher {

    private static final int INITIAL_BUFFER_SIZE = 64 * 1024;

    @Getter private static boolean active = false;
    private static DeferredBatchTessellator batchTessellator;

    private DeferredDrawBatcher() {}

    /**
     * Begin deferred capture mode. Called after startDrawingQuads().
     * Pushes a DeferredBatchTessellator onto the DirectTessellator stack so that
     * subsequent tessellator.draw() calls are intercepted and accumulated.
     */
    public static void enter() {
        active = true;
        if (batchTessellator == null) {
            batchTessellator = new DeferredBatchTessellator(memAlloc(INITIAL_BUFFER_SIZE));
        }
        batchTessellator.clearRanges();
        batchTessellator.snapshotDefaultModelview();
        TessellatorManager.startCapturingDirect(batchTessellator);
    }

    /**
     * Exit deferred mode and flush all captured batches. Groups entries by state key
     * and vertex format, issues one draw per unique group. Restores the GL state that
     * was active for the last group so vanilla sees consistent state.
     */
    public static void exitAndFlush() {
        active = false;
        final List<DeferredBatchTessellator.DrawRange> ranges = batchTessellator.getRanges();

        if (!ranges.isEmpty()) {
            ranges.sort(Comparator.comparingLong(DeferredBatchTessellator.DrawRange::stateKey));
            flushSorted(ranges);
        }

        TessellatorManager.stopCapturingDirect();
    }

    private static void flushSorted(List<DeferredBatchTessellator.DrawRange> ranges) {
        long currentKey = ranges.get(0).stateKey();
        int groupStart = 0;

        for (int i = 0; i <= ranges.size(); i++) {
            final boolean endOfList = (i == ranges.size());
            final boolean keyChanged = !endOfList && ranges.get(i).stateKey() != currentKey;

            if (endOfList || keyChanged) {
                applyStateKey(currentKey);
                flushGroup(ranges, groupStart, i);

                if (!endOfList) {
                    currentKey = ranges.get(i).stateKey();
                    groupStart = i;
                }
            }
        }
    }

    /**
     * Flush a group of ranges sharing the same state key.
     * Subgroups by (drawMode, format flags) since those must match for merged draws.
     */
    private static void flushGroup(List<DeferredBatchTessellator.DrawRange> ranges, int from, int to) {
        int i = from;
        while (i < to) {
            final DeferredBatchTessellator.DrawRange first = ranges.get(i);
            final int drawMode = first.drawMode();
            final int flags = first.flags();

            int totalBytes = 0;
            int totalVertices = 0;
            int subEnd = i;

            while (subEnd < to) {
                final DeferredBatchTessellator.DrawRange e = ranges.get(subEnd);
                if (e.drawMode() != drawMode || e.flags() != flags) break;
                totalBytes += e.byteLength();
                totalVertices += e.vertexCount();
                subEnd++;
            }

            TessellatorStreamingDrawer.drawPackedBatch(
                batchTessellator, ranges, i, subEnd, totalBytes, totalVertices, drawMode, flags);

            i = subEnd;
        }
    }

    /**
     * Pack current GLSM state into a long key for grouping.
     * Layout: [textureId:20][srcRgb:12][dstRgb:12][blendEnabled:1][depthMask:1] = 46 bits
     * GL blend constants (e.g. GL_SRC_ALPHA=0x0302) need 12 bits.
     *
     * NOT captured (uniform within a particle layer bracket — set once before startDrawingQuads,
     * not changed by individual particles):
     * - depth test enable (always GL_LEQUAL for particles)
     * - alpha test func/ref (set per-layer, not per-particle)
     * - separate blend alpha factors (srcAlpha/dstAlpha) — particles use glBlendFunc not glBlendFuncSeparate
     * - fog state (mode, color, start/end — constant for the frame)
     */
    static long captureStateKey() {
        final int textureId = GLStateManager.getTextures().getTextureUnitBindings(0).getBinding();
        final int srcRgb = GLStateManager.getBlendState().getSrcRgb();
        final int dstRgb = GLStateManager.getBlendState().getDstRgb();
        final boolean blendEnabled = GLStateManager.getBlendMode().isEnabled();
        final boolean depthMask = GLStateManager.getDepthState().isEnabled();

        return packStateKey(textureId, srcRgb, dstRgb, blendEnabled, depthMask);
    }

    static long packStateKey(int textureId, int srcRgb, int dstRgb, boolean blendEnabled, boolean depthMask) {
        return ((long) (textureId & 0xFFFFF) << 26)
            | ((long) (srcRgb & 0xFFF) << 14)
            | ((long) (dstRgb & 0xFFF) << 2)
            | (blendEnabled ? 2L : 0L)
            | (depthMask ? 1L : 0L);
    }

    static int unpackTextureId(long key) { return (int) ((key >> 26) & 0xFFFFF); }
    static int unpackSrcRgb(long key) { return (int) ((key >> 14) & 0xFFF); }
    static int unpackDstRgb(long key) { return (int) ((key >> 2) & 0xFFF); }
    static boolean unpackBlendEnabled(long key) { return (key & 2L) != 0; }
    static boolean unpackDepthMask(long key) { return (key & 1L) != 0; }

    static void applyStateKey(long key) {
        final int textureId = unpackTextureId(key);
        final int srcRgb = unpackSrcRgb(key);
        final int dstRgb = unpackDstRgb(key);
        final boolean blendEnabled = unpackBlendEnabled(key);
        final boolean depthMask = unpackDepthMask(key);

        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GLStateManager.glBlendFunc(srcRgb, dstRgb);
        if (blendEnabled) {
            GLStateManager.enableBlend();
        } else {
            GLStateManager.disableBlend();
        }
        GLStateManager.glDepthMask(depthMask);
    }
}
