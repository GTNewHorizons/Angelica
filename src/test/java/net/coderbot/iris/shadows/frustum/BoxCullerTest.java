package net.coderbot.iris.shadows.frustum;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoxCullerTest {

    private static BoxCuller culler(double maxDistance, double cameraX, double cameraY, double cameraZ) {
        final BoxCuller culler = new BoxCuller(maxDistance);
        culler.setPosition(cameraX, cameraY, cameraZ);
        return culler;
    }

    @Test
    void fullyInsideMatchesTheSnappedPaddedBounds() {
        final BoxCuller culler = culler(64.0, 8.0, 8.0, 8.0);

        assertTrue(culler.isFullyInsideViewRelative(-72, -72, -72, 72, 72, 72));
        assertTrue(culler.isFullyInsideViewRelative(-1, -1, -1, 1, 1, 1));
        assertFalse(culler.isFullyInsideViewRelative(-72.5f, 0, 0, 0, 0, 0));
        assertFalse(culler.isFullyInsideViewRelative(0, 0, 0, 0, 72.5f, 0));
        assertFalse(culler.isFullyInsideViewRelative(0, 0, -72.5f, 0, 0, 72.5f));
    }

    @Test
    void snappingIsNotCameraSymmetric() {
        final BoxCuller culler = culler(64.0, 0.0, 0.0, 0.0);

        assertTrue(culler.isFullyInsideViewRelative(-64, -64, -64, 80, 80, 80));
        assertFalse(culler.isFullyInsideViewRelative(-64.5f, 0, 0, 0, 0, 0));
        assertFalse(culler.isFullyInsideViewRelative(0, 0, 0, 80.5f, 0, 0));
    }

    @Test
    void fullyInsideNeverOverlapsCulled() {
        final BoxCuller culler = culler(48.0, 123.75, -7.5, 640.0);
        final Random random = new Random(20260906L);

        int fullyInside = 0;
        int culled = 0;
        int straddling = 0;

        for (int i = 0; i < 20000; i++) {
            final float minX = (random.nextFloat() * 2.0f - 1.0f) * 160.0f;
            final float minY = (random.nextFloat() * 2.0f - 1.0f) * 160.0f;
            final float minZ = (random.nextFloat() * 2.0f - 1.0f) * 160.0f;
            final float maxX = minX + random.nextFloat() * 48.0f;
            final float maxY = minY + random.nextFloat() * 48.0f;
            final float maxZ = minZ + random.nextFloat() * 48.0f;

            final boolean inside = culler.isFullyInsideViewRelative(minX, minY, minZ, maxX, maxY, maxZ);
            final boolean outside = culler.isCulledViewRelative(minX, minY, minZ, maxX, maxY, maxZ);
            final String box = minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ;

            assertFalse(inside && outside, box);

            if (inside) {
                fullyInside++;
                assertCornersKept(culler, minX, minY, minZ, maxX, maxY, maxZ, box);
            } else if (outside) {
                culled++;
            } else {
                straddling++;
            }
        }

        assertTrue(fullyInside > 0, "no fully inside samples");
        assertTrue(culled > 0, "no culled samples");
        assertTrue(straddling > 0, "no straddling samples");
    }

    private static void assertCornersKept(BoxCuller culler, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, String box) {
        for (int corner = 0; corner < 8; corner++) {
            final float x = (corner & 1) == 0 ? minX : maxX;
            final float y = (corner & 2) == 0 ? minY : maxY;
            final float z = (corner & 4) == 0 ? minZ : maxZ;

            assertFalse(culler.isCulledViewRelative(x, y, z, x, y, z), box + " corner " + corner);
            assertTrue(culler.isFullyInsideViewRelative(x, y, z, x, y, z), box + " corner " + corner);
        }
    }
}
