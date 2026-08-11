package com.gtnewhorizons.angelica.rendering.celeritas;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.util.AxisAlignedBB;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionFrustumGateTest {

    private static FrustumIntersection knownFrustum() {
        return new FrustumIntersection(new Matrix4f().perspective((float) Math.toRadians(90), 1.0f, 0.1f, 100.0f));
    }

    @Test
    void triStateVerdictsAgainstKnownPerspective() {
        final FrustumIntersection frustum = knownFrustum();

        assertEquals(FrustumIntersection.INSIDE, frustum.intersectAab(-1, -1, -50, 1, 1, -40));
        assertEquals(FrustumIntersection.INTERSECT, frustum.intersectAab(-1, -1, -5, 1, 1, 5));
        assertTrue(frustum.intersectAab(-1, -1, 10, 1, 1, 20) >= 0);
        assertTrue(frustum.intersectAab(-100, -1, -20, -50, 1, -10) >= 0);
    }

    @Test
    void sectionVerdictIsSoundForContainedBoxes() {
        final Random rng = new Random(0x5EC7104);

        for (int iter = 0; iter < 2000; iter++) {
            final Matrix4f m = new Matrix4f()
                .perspective((float) Math.toRadians(30 + rng.nextInt(90)), 0.5f + rng.nextFloat() * 2.0f, 0.05f, 64 + rng.nextFloat() * 448)
                .rotateX((rng.nextFloat() - 0.5f) * (float) Math.PI)
                .rotateY(rng.nextFloat() * 2.0f * (float) Math.PI);
            final FrustumIntersection frustum = new FrustumIntersection(m);

            for (int s = 0; s < 50; s++) {
                final float ox = (rng.nextFloat() - 0.5f) * 512;
                final float oy = (rng.nextFloat() - 0.5f) * 512;
                final float oz = (rng.nextFloat() - 0.5f) * 512;

                final int verdict = frustum.intersectAab(ox, oy, oz, ox + 16, oy + 16, oz + 16);

                final float lx = rng.nextFloat() * 15, ly = rng.nextFloat() * 15, lz = rng.nextFloat() * 15;
                final float hx = lx + rng.nextFloat() * (16 - lx);
                final float hy = ly + rng.nextFloat() * (16 - ly);
                final float hz = lz + rng.nextFloat() * (16 - lz);

                final boolean boxVisible = frustum.testAab(ox + lx, oy + ly, oz + lz, ox + hx, oy + hy, oz + hz);

                if (verdict == FrustumIntersection.INSIDE) {
                    assertTrue(boxVisible, "box in INSIDE section must be visible");
                } else if (verdict >= 0) {
                    assertFalse(boxVisible, "box in plane-culled section must be culled");
                }
            }
        }
    }

    @Test
    void packedLocalBoundsMatchLegacyDoubleFormulation() {
        final Random rng = new Random(0xA3B1D);

        for (int iter = 0; iter < 5000; iter++) {
            final int ox = (rng.nextInt(4_000_000) - 2_000_000) & ~15;
            final int oy = rng.nextInt(16) << 4;
            final int oz = (rng.nextInt(4_000_000) - 2_000_000) & ~15;

            final CameraTransform t = new CameraTransform(ox + (rng.nextDouble() - 0.5) * 1024, oy + (rng.nextDouble() - 0.5) * 256, oz + (rng.nextDouble() - 0.5) * 1024);

            final double minX = ox + rng.nextDouble() * 15;
            final double minY = oy + rng.nextDouble() * 15;
            final double minZ = oz + rng.nextDouble() * 15;
            final AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(minX, minY, minZ, minX + rng.nextDouble() * (ox + 16 - minX), minY + rng.nextDouble() * (oy + 16 - minY), minZ + rng.nextDouble() * (oz + 16 - minZ));

            final FloatArrayList packed = new FloatArrayList();
            AngelicaBuiltRenderSectionData.packSectionLocalBounds(packed, aabb, ox, oy, oz);

            final float fx = (ox - t.intX) - t.fracX;
            final float fy = (oy - t.intY) - t.fracY;
            final float fz = (oz - t.intZ) - t.fracZ;

            final double[] world = { aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ };
            final float[] origin = { fx, fy, fz, fx, fy, fz };
            final int[] camInt = { t.intX, t.intY, t.intZ, t.intX, t.intY, t.intZ };
            final float[] camFrac = { t.fracX, t.fracY, t.fracZ, t.fracX, t.fracY, t.fracZ };

            for (int i = 0; i < 6; i++) {
                final float legacyCoord = (float) (world[i] - camInt[i]) - camFrac[i];
                final float packedCoord = origin[i] + packed.getFloat(i);
                assertEquals(legacyCoord, packedCoord, 1e-3f, "coord " + i + " diverged at iter " + iter);
            }
        }
    }
}
