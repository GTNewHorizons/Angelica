package net.coderbot.iris.shadows.frustum;

import net.coderbot.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import net.coderbot.iris.shadows.frustum.advanced.SafeZoneCullingFrustum;
import net.coderbot.iris.shadows.frustum.fallback.BoxCullingFrustum;
import net.minecraft.client.renderer.culling.Frustrum;
import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static net.coderbot.iris.shadows.frustum.ShadowFrustumTestSupport.SUN_UP;
import static net.coderbot.iris.shadows.frustum.ShadowFrustumTestSupport.culler;
import static net.coderbot.iris.shadows.frustum.ShadowFrustumTestSupport.forEachSampleAab;
import static net.coderbot.iris.shadows.frustum.ShadowFrustumTestSupport.projection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowFrustumIntersectAabTest {
    private static AdvancedShadowCullingFrustum advanced(BoxCuller boxCuller) {
        final AdvancedShadowCullingFrustum frustum = new AdvancedShadowCullingFrustum();
        frustum.init(new Matrix4f(), projection(), SUN_UP, boxCuller);
        frustum.setPosition(8.0, 8.0, 8.0);
        return frustum;
    }

    private static SafeZoneCullingFrustum safeZone(BoxCuller voxelCuller, BoxCuller distanceCuller) {
        final SafeZoneCullingFrustum frustum = new SafeZoneCullingFrustum();
        frustum.init(new Matrix4f(), projection(), SUN_UP, voxelCuller, distanceCuller);
        frustum.setPosition(8.0, 8.0, 8.0);
        return frustum;
    }

    @Test
    void frustrumStubIsActive() {
        assertThrows(NoSuchFieldException.class, () -> Frustrum.class.getDeclaredField("clippingHelper"),
            "the real Frustrum's GL-touching clippingHelper field is on the classpath; the test stub did not win");
    }

    @Test
    void boxCullingFrustumReportsTriState() {
        final BoxCullingFrustum frustum = new BoxCullingFrustum(new BoxCuller(64.0));
        frustum.setPosition(8.0, 8.0, 8.0);

        assertEquals(Frustum.FULLY_INSIDE, frustum.intersectAab(-16, -16, -16, 16, 16, 16));
        assertEquals(Frustum.PARTIALLY_INSIDE, frustum.intersectAab(-16, -16, -16, 16, 16, 100));
        assertEquals(Frustum.OUTSIDE, frustum.intersectAab(-16, -16, 80, 16, 16, 100));
        assertConsistent(frustum, 1L);
    }

    @Test
    void advancedReportsTriStateWithoutABoxCuller() {
        final AdvancedShadowCullingFrustum frustum = advanced(null);

        assertEquals(Frustum.FULLY_INSIDE, frustum.intersectAab(-1, -1, -51, 1, 1, -49));
        assertEquals(Frustum.PARTIALLY_INSIDE, frustum.intersectAab(-1, -60, -51, 1, -40, -49));
        assertEquals(Frustum.OUTSIDE, frustum.intersectAab(-1, -1, 99, 1, 1, 101));
    }

    @Test
    void advancedDowngradesToPartialOutsideTheBoxCuller() {
        final AdvancedShadowCullingFrustum frustum = advanced(culler(64.0));

        assertEquals(Frustum.FULLY_INSIDE, frustum.intersectAab(-1, -1, -51, 1, 1, -49));
        assertEquals(Frustum.PARTIALLY_INSIDE, frustum.intersectAab(-1, -1, -80, 1, 1, -60));
        assertEquals(Frustum.OUTSIDE, frustum.intersectAab(-1, -1, -200, 1, 1, -190));
    }

    @Test
    void advancedAgreesWithTestAab() {
        assertConsistent(advanced(null), 20250906L);
        assertConsistent(advanced(culler(64.0)), 20250907L);
    }

    @Test
    void advancedReportsOcclusionSearchSupportAndLightVector() {
        final Vector3f light = new Vector3f(0.4f, 0.8f, 0.45f).normalize();
        final AdvancedShadowCullingFrustum frustum = new AdvancedShadowCullingFrustum();
        frustum.init(new Matrix4f(), projection(), light, null);

        assertTrue(frustum.supportsOcclusionSearch());
        assertEquals(light.x(), frustum.shadowLightX());
        assertEquals(light.y(), frustum.shadowLightY());
        assertEquals(light.z(), frustum.shadowLightZ());
    }

    @Test
    void safeZoneReportsTriState() {
        final SafeZoneCullingFrustum frustum = safeZone(culler(16.0), culler(96.0));

        assertEquals(Frustum.FULLY_INSIDE, frustum.intersectAab(-1, -1, -11, 1, 1, -9));
        assertEquals(Frustum.PARTIALLY_INSIDE, frustum.intersectAab(-1, -60, -51, 1, -40, -49));
        assertEquals(Frustum.OUTSIDE, frustum.intersectAab(-1, -1, 50, 1, 1, 60));
        assertEquals(Frustum.OUTSIDE, frustum.intersectAab(-1, -1, -200, 1, 1, -190));
    }

    @Test
    void safeZoneAgreesWithTestAab() {
        assertConsistent(safeZone(culler(16.0), culler(96.0)), 20250908L);
        assertConsistent(safeZone(culler(16.0), null), 20250909L);
        assertConsistent(safeZone(culler(96.0), culler(16.0)), 20250910L);
    }

    @Test
    void safeZoneLargerThanDistanceIsOnlyPartiallyInside() {
        final SafeZoneCullingFrustum frustum = safeZone(culler(96.0), culler(16.0));

        assertEquals(Frustum.PARTIALLY_INSIDE, frustum.intersectAab(-30, -1, -1, 30, 1, 1));
        assertEquals(Frustum.FULLY_INSIDE, frustum.intersectAab(-1, -1, -11, 1, 1, -9));
    }

    @Test
    void safeZoneDoesNotSupportOcclusionSearch() {
        final SafeZoneCullingFrustum frustum = safeZone(culler(16.0), culler(96.0));

        assertFalse(frustum.supportsOcclusionSearch());
    }

    private static void assertConsistent(Frustum frustum, long seed) {
        final AtomicInteger fullyInsideCount = new AtomicInteger();
        final AtomicInteger partiallyInsideCount = new AtomicInteger();
        final AtomicInteger outsideCount = new AtomicInteger();

        forEachSampleAab(seed, (minX, minY, minZ, maxX, maxY, maxZ) -> {
            final int result = frustum.intersectAab(minX, minY, minZ, maxX, maxY, maxZ);

            assertEquals(frustum.testAab(minX, minY, minZ, maxX, maxY, maxZ), result != Frustum.OUTSIDE,
                () -> minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ);

            if (result == Frustum.FULLY_INSIDE) {
                fullyInsideCount.incrementAndGet();

                for (int corner = 0; corner < 8; corner++) {
                    final float x = (corner & 1) == 0 ? minX : maxX;
                    final float y = (corner & 2) == 0 ? minY : maxY;
                    final float z = (corner & 4) == 0 ? minZ : maxZ;
                    final int cornerIndex = corner;

                    assertTrue(frustum.testAab(x, y, z, x, y, z),
                        () -> minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ
                            + " corner " + cornerIndex);
                }
            } else if (result == Frustum.PARTIALLY_INSIDE) {
                partiallyInsideCount.incrementAndGet();
            } else {
                outsideCount.incrementAndGet();
            }
        });

        assertTrue(fullyInsideCount.get() > 0, "no fully inside samples");
        assertTrue(partiallyInsideCount.get() > 0, "no partially inside samples");
        assertTrue(outsideCount.get() > 0, "no outside samples");
    }
}
