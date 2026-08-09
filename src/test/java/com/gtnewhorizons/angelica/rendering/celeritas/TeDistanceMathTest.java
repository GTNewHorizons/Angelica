package com.gtnewhorizons.angelica.rendering.celeritas;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeDistanceMathTest {

    private static double distSq(double x1, double y1, double z1, double x2, double y2, double z2) {
        final double dx = x1 - x2, dy = y1 - y2, dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    @Test
    void cameraInsideSectionIsZero() {
        assertEquals(0.0, TeDistanceMath.distSqToSection(8, 8, 8, 0, 0, 0));
        assertEquals(0.0, TeDistanceMath.distSqToSection(16, 0, 16, 0, 0, 0));
        assertEquals(0.0, TeDistanceMath.distSqToSection(-32, 5, 100.5, -32, 0, 96));
    }

    @Test
    void matchesClampFormulation() {
        final Random rng = new Random(0xD157);
        for (int iter = 0; iter < 10_000; iter++) {
            final int ox = (rng.nextInt(4_000_000) - 2_000_000) & ~15;
            final int oy = rng.nextInt(16) << 4;
            final int oz = (rng.nextInt(4_000_000) - 2_000_000) & ~15;
            final double camX = ox + (rng.nextDouble() - 0.5) * 512;
            final double camY = oy + (rng.nextDouble() - 0.5) * 512;
            final double camZ = oz + (rng.nextDouble() - 0.5) * 512;

            final double cx = Math.max(ox, Math.min(camX, ox + 16));
            final double cy = Math.max(oy, Math.min(camY, oy + 16));
            final double cz = Math.max(oz, Math.min(camZ, oz + 16));
            final double expected = distSq(camX, camY, camZ, cx, cy, cz);

            assertEquals(expected, TeDistanceMath.distSqToSection(camX, camY, camZ, ox, oy, oz), 1e-9, "clamp formulation diverged at iter " + iter);
        }
    }

    @Test
    void lowerBoundsEveryPointInSection() {
        final Random rng = new Random(0xB0C5);
        for (int iter = 0; iter < 2_000; iter++) {
            final int ox = (rng.nextInt(200_000) - 100_000) & ~15;
            final int oy = rng.nextInt(16) << 4;
            final int oz = (rng.nextInt(200_000) - 100_000) & ~15;
            final double camX = ox + (rng.nextDouble() - 0.5) * 256;
            final double camY = oy + (rng.nextDouble() - 0.5) * 256;
            final double camZ = oz + (rng.nextDouble() - 0.5) * 256;

            final double sectionDistSq = TeDistanceMath.distSqToSection(camX, camY, camZ, ox, oy, oz);

            for (int s = 0; s < 30; s++) {
                final double px = ox + rng.nextDouble() * 16;
                final double py = oy + rng.nextDouble() * 16;
                final double pz = oz + rng.nextDouble() * 16;
                final double pointDistSq = distSq(camX, camY, camZ, px, py, pz);
                assertTrue(pointDistSq >= sectionDistSq - 1e-9, "point dist " + pointDistSq + " < section dist " + sectionDistSq + " at iter " + iter);
            }
        }
    }
}
