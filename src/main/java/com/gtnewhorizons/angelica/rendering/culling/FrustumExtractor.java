package com.gtnewhorizons.angelica.rendering.culling;

import org.joml.Matrix4fc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class FrustumExtractor {

    public static final int UBO_SIZE_BYTES = 160;
    public static final int PLANE_COUNT = 6;

    private static final int PLANE_STRIDE = 16;
    private static final int CONTROL_OFFSET = 96;
    private static final int CAMERA_WORLD_OFFSET = 112;
    private static final int BATCH_OFFSET = 128;
    private static final int PYR_OFFSET = 144;

    private FrustumExtractor() {}

    public static ByteBuffer allocateUboByteBuffer() {
        return ByteBuffer.allocateDirect(UBO_SIZE_BYTES).order(ByteOrder.nativeOrder());
    }

    public static void writeStd140(Matrix4fc mvp, int visibleCount, int indexPointerMask, ByteBuffer out) {
        final float r0x = mvp.m00(), r0y = mvp.m10(), r0z = mvp.m20(), r0w = mvp.m30();
        final float r1x = mvp.m01(), r1y = mvp.m11(), r1z = mvp.m21(), r1w = mvp.m31();
        final float r2x = mvp.m02(), r2y = mvp.m12(), r2z = mvp.m22(), r2w = mvp.m32();
        final float r3x = mvp.m03(), r3y = mvp.m13(), r3z = mvp.m23(), r3w = mvp.m33();

        writePlane(out, 0 * PLANE_STRIDE, r3x + r0x, r3y + r0y, r3z + r0z, r3w + r0w);
        writePlane(out, 1 * PLANE_STRIDE, r3x - r0x, r3y - r0y, r3z - r0z, r3w - r0w);
        writePlane(out, 2 * PLANE_STRIDE, r3x + r1x, r3y + r1y, r3z + r1z, r3w + r1w);
        writePlane(out, 3 * PLANE_STRIDE, r3x - r1x, r3y - r1y, r3z - r1z, r3w - r1w);
        writePlane(out, 4 * PLANE_STRIDE, r3x + r2x, r3y + r2y, r3z + r2z, r3w + r2w);
        writePlane(out, 5 * PLANE_STRIDE, r3x - r2x, r3y - r2y, r3z - r2z, r3w - r2w);

        out.putInt(CONTROL_OFFSET + 0,  visibleCount);
        out.putInt(CONTROL_OFFSET + 4,  indexPointerMask);
        out.putInt(CONTROL_OFFSET + 8,  0);
        out.putInt(CONTROL_OFFSET + 12, 0);
        out.putInt(BATCH_OFFSET, 0);
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

    public static void patchCameraWorld(float x, float y, float z, ByteBuffer out) {
        out.putFloat(CAMERA_WORLD_OFFSET + 0, x);
        out.putFloat(CAMERA_WORLD_OFFSET + 4, y);
        out.putFloat(CAMERA_WORLD_OFFSET + 8, z);
    }

    private static void writePlane(ByteBuffer out, int offset, float a, float b, float c, float d) {
        final float invLen = 1.0f / (float) Math.sqrt(a * a + b * b + c * c);
        out.putFloat(offset + 0,  a * invLen);
        out.putFloat(offset + 4,  b * invLen);
        out.putFloat(offset + 8,  c * invLen);
        out.putFloat(offset + 12, d * invLen);
    }
}
