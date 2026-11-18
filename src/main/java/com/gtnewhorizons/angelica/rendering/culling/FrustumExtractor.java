package com.gtnewhorizons.angelica.rendering.culling;

import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class FrustumExtractor {

    public static final int UBO_SIZE_BYTES = 160;
    public static final int PLANE_COUNT = 6;

    private static final float SECTION_HALF_EXTENT = 8.0f + 1.0f + 0.125f;
    private static final float SECTION_CENTER = 8.0f;
    private static final int PLANE_STRIDE = 16;
    private static final int CONTROL_OFFSET = 96;
    private static final int CAMERA_BLOCK_OFFSET = 112;
    private static final int BATCH_OFFSET = 128;
    private static final int PYR_OFFSET = 144;

    private FrustumExtractor() {}

    public static ByteBuffer allocateUboByteBuffer() {
        return ByteBuffer.allocateDirect(UBO_SIZE_BYTES).order(ByteOrder.nativeOrder());
    }

    public static void writeFrustum(Matrix4fc projection, Matrix4fc modelView, CameraTransform camera, Matrix4f mvp, ByteBuffer out) {
        projection.mul(modelView, mvp);
        final float r0x = mvp.m00(), r0y = mvp.m10(), r0z = mvp.m20(), r0w = mvp.m30();
        final float r1x = mvp.m01(), r1y = mvp.m11(), r1z = mvp.m21(), r1w = mvp.m31();
        final float r2x = mvp.m02(), r2y = mvp.m12(), r2z = mvp.m22(), r2w = mvp.m32();
        final float r3x = mvp.m03(), r3y = mvp.m13(), r3z = mvp.m23(), r3w = mvp.m33();
        final float cx = SECTION_CENTER - camera.fracX;
        final float cy = SECTION_CENTER - camera.fracY;
        final float cz = SECTION_CENTER - camera.fracZ;

        writePlane(out, 0 * PLANE_STRIDE, r3x + r0x, r3y + r0y, r3z + r0z, r3w + r0w, cx, cy, cz);
        writePlane(out, 1 * PLANE_STRIDE, r3x - r0x, r3y - r0y, r3z - r0z, r3w - r0w, cx, cy, cz);
        writePlane(out, 2 * PLANE_STRIDE, r3x + r1x, r3y + r1y, r3z + r1z, r3w + r1w, cx, cy, cz);
        writePlane(out, 3 * PLANE_STRIDE, r3x - r1x, r3y - r1y, r3z - r1z, r3w - r1w, cx, cy, cz);
        writePlane(out, 4 * PLANE_STRIDE, r3x + r2x, r3y + r2y, r3z + r2z, r3w + r2w, cx, cy, cz);
        writePlane(out, 5 * PLANE_STRIDE, r3x - r2x, r3y - r2y, r3z - r2z, r3w - r2w, cx, cy, cz);

        out.putInt(CAMERA_BLOCK_OFFSET + 0,  camera.intX);
        out.putInt(CAMERA_BLOCK_OFFSET + 4,  camera.intY);
        out.putInt(CAMERA_BLOCK_OFFSET + 8,  camera.intZ);
    }

    public static void patchBatchEntryBase(int entryBase, ByteBuffer out) {
        out.putInt(BATCH_OFFSET, entryBase);
    }

    /** Small exact integers, so the float round trip through the UBO is lossless. */
    public static void patchPrimitiveRatio(int verticesPerPrimitive, int elementsPerPrimitive, ByteBuffer out) {
        out.putFloat(PYR_OFFSET + 8,  (float) verticesPerPrimitive);
        out.putFloat(PYR_OFFSET + 12, (float) elementsPerPrimitive);
    }

    public static void patchControl(int visibleCount, int indexPointerMask, ByteBuffer out) {
        out.putInt(CONTROL_OFFSET + 0, visibleCount);
        out.putInt(CONTROL_OFFSET + 4, indexPointerMask);
    }

    public static void patchBypassFrustum(boolean bypass, ByteBuffer out) {
        out.putInt(CONTROL_OFFSET + 8, bypass ? 1 : 0);
    }

    private static void writePlane(ByteBuffer out, int offset, float a, float b, float c, float d, float cx, float cy, float cz) {
        out.putFloat(offset + 0,  a);
        out.putFloat(offset + 4,  b);
        out.putFloat(offset + 8,  c);
        out.putFloat(offset + 12, d + SECTION_HALF_EXTENT * (Math.abs(a) + Math.abs(b) + Math.abs(c)) + a * cx + b * cy + c * cz);
    }
}
