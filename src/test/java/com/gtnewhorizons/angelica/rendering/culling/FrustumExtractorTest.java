package com.gtnewhorizons.angelica.rendering.culling;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrustumExtractorTest {
    private static final float EPS = 1e-4f;

    @Test
    void uboSizeIs160() {
        assertEquals(160, FrustumExtractor.UBO_SIZE_BYTES);
        assertEquals(0, FrustumExtractor.UBO_SIZE_BYTES % 16, "std140 requires a 16-byte multiple");
    }

    @Test
    void patchBatchEntryBase_writesAtOffset128_andWriteStd140Resets() {
        final ByteBuffer out = FrustumExtractor.allocateUboByteBuffer();
        FrustumExtractor.patchBatchEntryBase(777, out);
        assertEquals(777, out.getInt(128));
        FrustumExtractor.writeStd140(new Matrix4f(), 0, 0, out);
        assertEquals(0, out.getInt(128), "writeStd140 must reset entryBase");
    }

    @Test
    void identityMatrix_planesAreUnitNormalsAtUnitDistance() {
        final Matrix4f identity = new Matrix4f();
        final ByteBuffer out = FrustumExtractor.allocateUboByteBuffer();
        FrustumExtractor.writeStd140(identity, 0, 0, out);

        assertPlane(out,  0,  1f,  0f,  0f, 1f);
        assertPlane(out, 16, -1f,  0f,  0f, 1f);
        assertPlane(out, 32,  0f,  1f,  0f, 1f);
        assertPlane(out, 48,  0f, -1f,  0f, 1f);
        assertPlane(out, 64,  0f,  0f,  1f, 1f);
        assertPlane(out, 80,  0f,  0f, -1f, 1f);
    }

    @Test
    void identityMatrix_left_correct() {
        final Matrix4f identity = new Matrix4f();
        final ByteBuffer out = FrustumExtractor.allocateUboByteBuffer();
        FrustumExtractor.writeStd140(identity, 0, 0, out);
        assertPlane(out, 0, 1f, 0f, 0f, 1f);
    }

    @Test
    void perspectiveAxisAlignedPoint_insideFrustum() {
        final Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), 16f / 9f, 0.1f, 1000.0f);
        final ByteBuffer out = FrustumExtractor.allocateUboByteBuffer();
        FrustumExtractor.writeStd140(proj, 0, 0, out);

        assertTrue(insideAllPlanes(out, new Vector3f(0f, 0f, -10f)),  "point ahead should be inside");
        assertTrue(!insideAllPlanes(out, new Vector3f(0f, 0f, 10f)),  "point behind should be outside");
    }

    @Test
    void visibleCountStored() {
        final Matrix4f mvp = new Matrix4f();
        final ByteBuffer out = FrustumExtractor.allocateUboByteBuffer();
        FrustumExtractor.writeStd140(mvp, 4242, 0, out);
        assertEquals(4242, out.getInt(96), "visibleCount at offset 96");
        assertEquals(0,    out.getInt(100));
        assertEquals(0,    out.getInt(104));
        assertEquals(0,    out.getInt(108));
    }

    private static void assertPlane(ByteBuffer out, int offset, float a, float b, float c, float d) {
        final float len = (float) Math.sqrt(a * a + b * b + c * c);
        final float inv = len == 0.0f ? 1.0f : 1.0f / len;
        assertEquals(a * inv, out.getFloat(offset + 0),  EPS, "a @" + offset);
        assertEquals(b * inv, out.getFloat(offset + 4),  EPS, "b @" + offset);
        assertEquals(c * inv, out.getFloat(offset + 8),  EPS, "c @" + offset);
        assertEquals(d * inv, out.getFloat(offset + 12), EPS, "d @" + offset);
    }

    private static boolean insideAllPlanes(ByteBuffer out, Vector3f p) {
        for (int i = 0; i < 6; i++) {
            final float a = out.getFloat(i * 16 + 0);
            final float b = out.getFloat(i * 16 + 4);
            final float c = out.getFloat(i * 16 + 8);
            final float d = out.getFloat(i * 16 + 12);
            if (a * p.x + b * p.y + c * p.z + d < 0.0f) return false;
        }
        return true;
    }
}
