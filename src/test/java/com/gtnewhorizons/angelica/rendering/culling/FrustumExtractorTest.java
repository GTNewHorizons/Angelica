package com.gtnewhorizons.angelica.rendering.culling;

import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrustumExtractorTest {
    private static final float EPS = 1e-4f;
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final CameraTransform ORIGIN = new CameraTransform(0.0, 0.0, 0.0);
    private static final float EYE_HEIGHT = 0.12f;
    private static final double SECTION_PAD = 1.125;
    private static final float HALF_EXTENT = 8f + (float) SECTION_PAD;
    private static final double BOUNDARY_SKIP = 1e-3;
    private static final int SWEEP_SECTIONS = 6;

    @Test
    void uboSizeIs160() {
        assertEquals(160, FrustumExtractor.UBO_SIZE_BYTES);
        assertEquals(0, FrustumExtractor.UBO_SIZE_BYTES % 16, "std140 requires a 16-byte multiple");
    }

    @Test
    void patchBatchEntryBase_writesAtOffset128() {
        final ByteBuffer out = FrustumExtractor.allocateUboByteBuffer();
        FrustumExtractor.patchBatchEntryBase(777, out);
        assertEquals(777, out.getInt(128));
    }

    @Test
    void cameraFractionFoldsIntoPlaneConstant() {
        final CameraTransform camera = new CameraTransform(-3.25, 70.5, 12.75);
        final ByteBuffer out = FrustumExtractor.allocateUboByteBuffer();
        FrustumExtractor.writeFrustum(IDENTITY, IDENTITY, camera, new Matrix4f(), out);

        assertEquals(1f + HALF_EXTENT + (8f + 0.25f), out.getFloat(12), EPS);
        assertEquals(1f + HALF_EXTENT - (8f + 0.25f), out.getFloat(28), EPS);
        assertEquals(1f + HALF_EXTENT + (8f - 0.5f), out.getFloat(44), EPS);
        assertEquals(1f + HALF_EXTENT - (8f - 0.5f), out.getFloat(60), EPS);
        assertEquals(1f + HALF_EXTENT + (8f - 0.75f), out.getFloat(76), EPS);
        assertEquals(1f + HALF_EXTENT - (8f - 0.75f), out.getFloat(92), EPS);
        assertEquals(-3, out.getInt(112));
        assertEquals(70, out.getInt(116));
        assertEquals(12, out.getInt(120));
        assertEquals(0, out.getInt(124));
    }

    @Test
    void perspectiveAxisAlignedSection_insideFrustum() {
        final Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), 16f / 9f, 0.1f, 1000.0f);
        final ByteBuffer out = FrustumExtractor.allocateUboByteBuffer();
        FrustumExtractor.writeFrustum(proj, IDENTITY, ORIGIN, new Matrix4f(), out);

        assertTrue(shaderInsideFrustum(out, -8, -8, -32), "section ahead should be inside");
        assertTrue(!shaderInsideFrustum(out, -8, -8, 16), "section behind should be outside");
    }

    @Test
    void visibleCountStored() {
        final ByteBuffer out = FrustumExtractor.allocateUboByteBuffer();
        FrustumExtractor.patchControl(4242, 0, out);
        assertEquals(4242, out.getInt(96), "visibleCount at offset 96");
        assertEquals(0,    out.getInt(100));
        assertEquals(0,    out.getInt(104));
        assertEquals(0,    out.getInt(108));
    }

    @Test
    void matchesCpuFrustumForVanillaCameras() {
        final float[] distances = { 0.1f, 4.0f };
        final double[][] cameras = { { 8.3, 70.6, 8.7 }, { -1234.4, 65.2, -987.6 }, { 3000000.37, 80.5, -2999999.63 } };
        final float[] yaws = { 0f, 37f, 90f, 163f, 271f };
        final float[] pitches = { -60f, 0f, 25f, 80f };
        final Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(69.0), 16f / 9f, 0.05f, 512.0f);
        final Matrix4f mvFull = new Matrix4f();
        final Matrix4f refMvp = new Matrix4f();
        final Matrix4f mvp = new Matrix4f();
        final FrustumIntersection reference = new FrustumIntersection();
        final ByteBuffer out = FrustumExtractor.allocateUboByteBuffer();
        final StringBuilder report = new StringBuilder();
        int mismatches = 0;

        for (final float d : distances) {
            for (final double[] cam : cameras) {
                final CameraTransform camera = new CameraTransform(cam[0], cam[1], cam[2]);
                final int baseX = Math.floorDiv((int) Math.floor(cam[0]), 16);
                final int baseY = Math.floorDiv((int) Math.floor(cam[1]), 16);
                final int baseZ = Math.floorDiv((int) Math.floor(cam[2]), 16);
                int tested = 0;
                int wronglyCulled = 0;
                int wronglyKept = 0;

                for (final float yaw : yaws) {
                    for (final float pitch : pitches) {
                        mvFull.identity().translate(0f, 0f, -d).rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw + 180.0f)).translate(0f, EYE_HEIGHT, 0f);
                        reference.set(proj.mul(mvFull, refMvp));
                        FrustumExtractor.writeFrustum(proj, mvFull, camera, mvp, out);

                        for (int sx = -SWEEP_SECTIONS; sx <= SWEEP_SECTIONS; sx++) {
                            for (int sy = -SWEEP_SECTIONS; sy <= SWEEP_SECTIONS; sy++) {
                                for (int sz = -SWEEP_SECTIONS; sz <= SWEEP_SECTIONS; sz++) {
                                    final int ox = (baseX + sx) * 16;
                                    final int oy = (baseY + sy) * 16;
                                    final int oz = (baseZ + sz) * 16;
                                    final boolean tight = referenceVisible(reference, camera, ox, oy, oz, SECTION_PAD - BOUNDARY_SKIP);
                                    final boolean loose = referenceVisible(reference, camera, ox, oy, oz, SECTION_PAD + BOUNDARY_SKIP);
                                    if (tight != loose) continue;
                                    final boolean expected = tight;
                                    final boolean actual = shaderInsideFrustum(out, ox, oy, oz);
                                    tested++;
                                    if (expected && !actual) wronglyCulled++;
                                    if (!expected && actual) wronglyKept++;
                                }
                            }
                        }
                    }
                }

                mismatches += wronglyCulled + wronglyKept;
                report.append("\n  d=").append(d).append(" camera=(").append(cam[0]).append(", ").append(cam[1]).append(", ").append(cam[2]).append("): tested=").append(tested).append(" wronglyCulled=").append(wronglyCulled).append(" wronglyKept=").append(wronglyKept);
            }
        }

        assertEquals(0, mismatches, "shader frustum test disagrees with the CPU frustum:" + report);
    }

    private static boolean referenceVisible(FrustumIntersection reference, CameraTransform camera, int ox, int oy, int oz, double pad) {
        final float minX = (float) (ox - pad - camera.x);
        final float minY = (float) (oy - pad - camera.y);
        final float minZ = (float) (oz - pad - camera.z);
        final float maxX = (float) (ox + 16.0 + pad - camera.x);
        final float maxY = (float) (oy + 16.0 + pad - camera.y);
        final float maxZ = (float) (oz + 16.0 + pad - camera.z);
        return reference.testAab(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean shaderInsideFrustum(ByteBuffer out, int ox, int oy, int oz) {
        final float x = (float) (ox - out.getInt(112));
        final float y = (float) (oy - out.getInt(116));
        final float z = (float) (oz - out.getInt(120));
        for (int i = 0; i < 6; i++) {
            final float a = out.getFloat(i * 16 + 0);
            final float b = out.getFloat(i * 16 + 4);
            final float c = out.getFloat(i * 16 + 8);
            final float w = out.getFloat(i * 16 + 12);
            if (a * x + b * y + c * z + w < 0.0f) return false;
        }
        return true;
    }
}
