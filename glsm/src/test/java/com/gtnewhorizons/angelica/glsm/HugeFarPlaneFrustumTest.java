package com.gtnewhorizons.angelica.glsm;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HugeFarPlaneFrustumTest {

    private static final float SECTION_HALF = 8.0f + 1.0f + 0.125f;
    private static final float DEGENERATE_FAR = 2.147483648e9f;

    private static Matrix4f combined(float zFar) {
        final Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), 854.0f / 480.0f, 0.05f, zFar);
        final Matrix4f mv = new Matrix4f()
            .rotate((float) Math.toRadians(25.0), 1, 0, 0)
            .rotate((float) Math.toRadians(123.0), 0, 1, 0)
            .translate(0.0f, -1.62f, 0.0f);
        return new Matrix4f().set(proj).mul(mv);
    }

    private static boolean sectionVisible(FrustumIntersection f, int sx, int sy, int sz) {
        final float cx = sx * 16.0f + 8.0f;
        final float cy = sy * 16.0f + 8.0f;
        final float cz = sz * 16.0f + 8.0f;
        return f.testAab(cx - SECTION_HALF, cy - SECTION_HALF, cz - SECTION_HALF, cx + SECTION_HALF, cy + SECTION_HALF, cz + SECTION_HALF);
    }

    @Test
    void unnormalizedFrustumMatchesNormalFarPlane() {
        final FrustumIntersection normal = new FrustumIntersection(combined(512.0f), false);
        final FrustumIntersection huge = new FrustumIntersection(combined(DEGENERATE_FAR), false);

        int visible = 0;
        int mismatches = 0;
        for (int x = -12; x <= 12; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -12; z <= 12; z++) {
                    final boolean vNormal = sectionVisible(normal, x, y, z);
                    if (vNormal) visible++;
                    if (vNormal != sectionVisible(huge, x, y, z)) mismatches++;
                }
            }
        }
        assertTrue(visible > 100, "sanity check broken: normal frustum accepted only " + visible + " sections");
        assertEquals(0, mismatches, "sections culled differently under the degenerate far plane");
    }

    @Test
    void normalizedSetCullsEverythingOnDegenerateFarPlane() {
        final FrustumIntersection normalized = new FrustumIntersection();
        normalized.set(combined(DEGENERATE_FAR));

        int visible = 0;
        for (int x = -12; x <= 12; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -12; z <= 12; z++) {
                    if (sectionVisible(normalized, x, y, z)) visible++;
                }
            }
        }
        assertEquals(0, visible, "set(m) with a degenerate far plane no longer culls everything");
    }

    @Test
    void identityFrustumOnlyPassesCameraSection() {
        final FrustumIntersection identity = new FrustumIntersection(new Matrix4f());
        assertTrue(sectionVisible(identity, 0, 0, 0), "identity frustum rejected the camera's own section");
        int visible = 0;
        for (int x = -12; x <= 12; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -12; z <= 12; z++) {
                    if (Math.abs(x) <= 1 && Math.abs(y) <= 1 && Math.abs(z) <= 1) continue;
                    if (sectionVisible(identity, x, y, z)) visible++;
                }
            }
        }
        assertEquals(0, visible, "non-adjacent sections accepted by the identity frustum");
    }
}
