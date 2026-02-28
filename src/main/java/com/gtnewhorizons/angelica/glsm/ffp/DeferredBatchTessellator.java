package com.gtnewhorizons.angelica.glsm.ffp;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.renderer.Tessellator;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCopy;

/**
 * DirectTessellator subclass for deferred particle batching. Accumulates vertex data from
 * multiple interceptDraw() calls, recording per-draw metadata as {@link DrawRange} entries.
 * Computes a delta modelview matrix at each intercept to pre-transform vertices.
 */
class DeferredBatchTessellator extends DirectTessellator {

    @Desugar
    record DrawRange(long stateKey, int byteOffset, int byteLength, int vertexCount, int drawMode, int flags) {}

    private final List<DrawRange> ranges = new ArrayList<>();
    private final Matrix4f defaultModelview = new Matrix4f();
    private final Matrix4f defaultModelviewInverse = new Matrix4f();
    private final Matrix4f deltaMatrix = new Matrix4f(); // reusable scratch
    private final Vector3f scratch = new Vector3f();
    private Matrix4fc currentTransform;

    DeferredBatchTessellator(ByteBuffer buffer) {
        super(buffer, false); // deleteAfter=false: keep buffer across resets for reuse
    }

    @Override
    protected long writeVertexData(VertexFormat format, int[] rawBuffer, int rawBufferIndex) {
        if (currentTransform != null) {
            return format.writeToBuffer0(writePtr, rawBuffer, rawBufferIndex, currentTransform, scratch);
        }
        return super.writeVertexData(format, rawBuffer, rawBufferIndex);
    }

    /** Snapshot the current modelview as the "default" for this bracket. */
    void snapshotDefaultModelview() {
        defaultModelview.set(GLStateManager.getModelViewMatrix());
        defaultModelview.invert(defaultModelviewInverse);
    }

    @Override
    protected int interceptDraw(Tessellator tess) {
        // Compute delta: if current MV differs from default, pre-transform vertices
        final Matrix4f currentMV = GLStateManager.getModelViewMatrix();
        if (!currentMV.equals(defaultModelview)) {
            defaultModelviewInverse.mul(currentMV, deltaMatrix);
            this.currentTransform = deltaMatrix;
        } else {
            this.currentTransform = null;
        }

        final int byteOffsetBefore = bufferLimit();
        final int vertCount = tess.vertexCount;
        final int drawMode = tess.drawMode;
        final int flags = VertexFlags.convertToFlags(tess.hasTexture, tess.hasColor, tess.hasNormals, tess.hasBrightness);
        final long stateKey = DeferredDrawBatcher.captureStateKey();

        final int result = super.interceptDraw(tess); // repacks int[] → ByteBuffer + transforms if vertexTransform set

        final int byteOffsetAfter = bufferLimit();
        ranges.add(new DrawRange(stateKey, byteOffsetBefore, byteOffsetAfter - byteOffsetBefore, vertCount, drawMode, flags));
        this.currentTransform = null; // clear for next draw
        return result;
    }

    List<DrawRange> getRanges() { return ranges; }

    void clearRanges() { ranges.clear(); }

    void copyRange(int byteOffset, int byteLength, long destAddress) {
        memCopy(startPtr + byteOffset, destAddress, byteLength);
    }

    @Override
    protected void onRemovedFromStack() {
        clearRanges();
        super.onRemovedFromStack(); // calls reset(), keeps baseBuffer (deleteAfter=false)
    }
}
