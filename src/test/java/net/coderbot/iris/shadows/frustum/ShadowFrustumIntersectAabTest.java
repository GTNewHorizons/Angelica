package net.coderbot.iris.shadows.frustum;

import net.coderbot.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import net.coderbot.iris.shadows.frustum.advanced.SafeZoneCullingFrustum;
import net.coderbot.iris.shadows.frustum.fallback.BoxCullingFrustum;
import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static net.coderbot.iris.shadows.frustum.ShadowFrustumTestSupport.SUN_UP;
import static net.coderbot.iris.shadows.frustum.ShadowFrustumTestSupport.culler;
import static net.coderbot.iris.shadows.frustum.ShadowFrustumTestSupport.forEachSampleAab;
import static net.coderbot.iris.shadows.frustum.ShadowFrustumTestSupport.projection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("triStateRows")
    void intersectAabReportsTriState(String label, Supplier<Frustum> frustum, int expected, float[] box) {
        assertEquals(expected, frustum.get().intersectAab(box[0], box[1], box[2], box[3], box[4], box[5]));
    }

    private static Stream<Arguments> triStateRows() {
        final Supplier<Frustum> boxCulling = () -> {
            final BoxCullingFrustum frustum = new BoxCullingFrustum(new BoxCuller(64.0));
            frustum.setPosition(8.0, 8.0, 8.0);
            return frustum;
        };
        final Supplier<Frustum> advancedNoCuller = () -> advanced(null);
        final Supplier<Frustum> advancedCuller = () -> advanced(culler(64.0));
        final Supplier<Frustum> safeZoneSmallVoxel = () -> safeZone(culler(16.0), culler(96.0));
        final Supplier<Frustum> safeZoneLargeVoxel = () -> safeZone(culler(96.0), culler(16.0));
        return Stream.of(
            Arguments.of("boxCulling fully", boxCulling, Frustum.FULLY_INSIDE, new float[] {-16, -16, -16, 16, 16, 16}),
            Arguments.of("boxCulling partially", boxCulling, Frustum.PARTIALLY_INSIDE, new float[] {-16, -16, -16, 16, 16, 100}),
            Arguments.of("boxCulling outside", boxCulling, Frustum.OUTSIDE, new float[] {-16, -16, 80, 16, 16, 100}),
            Arguments.of("advanced no culler fully", advancedNoCuller, Frustum.FULLY_INSIDE, new float[] {-1, -1, -51, 1, 1, -49}),
            Arguments.of("advanced no culler partially", advancedNoCuller, Frustum.PARTIALLY_INSIDE, new float[] {-1, -60, -51, 1, -40, -49}),
            Arguments.of("advanced no culler outside", advancedNoCuller, Frustum.OUTSIDE, new float[] {-1, -1, 99, 1, 1, 101}),
            Arguments.of("advanced culler fully", advancedCuller, Frustum.FULLY_INSIDE, new float[] {-1, -1, -51, 1, 1, -49}),
            Arguments.of("advanced culler downgrades to partially", advancedCuller, Frustum.PARTIALLY_INSIDE, new float[] {-1, -1, -80, 1, 1, -60}),
            Arguments.of("advanced culler outside", advancedCuller, Frustum.OUTSIDE, new float[] {-1, -1, -200, 1, 1, -190}),
            Arguments.of("safeZone 16/96 fully", safeZoneSmallVoxel, Frustum.FULLY_INSIDE, new float[] {-1, -1, -11, 1, 1, -9}),
            Arguments.of("safeZone 16/96 partially", safeZoneSmallVoxel, Frustum.PARTIALLY_INSIDE, new float[] {-1, -60, -51, 1, -40, -49}),
            Arguments.of("safeZone 16/96 outside near", safeZoneSmallVoxel, Frustum.OUTSIDE, new float[] {-1, -1, 50, 1, 1, 60}),
            Arguments.of("safeZone 16/96 outside far", safeZoneSmallVoxel, Frustum.OUTSIDE, new float[] {-1, -1, -200, 1, 1, -190}),
            Arguments.of("safeZone 96/16 wide box partially", safeZoneLargeVoxel, Frustum.PARTIALLY_INSIDE, new float[] {-30, -1, -1, 30, 1, 1}),
            Arguments.of("safeZone 96/16 narrow box fully", safeZoneLargeVoxel, Frustum.FULLY_INSIDE, new float[] {-1, -1, -11, 1, 1, -9}));
    }

    @Test
    void boxCullingFrustumAgreesWithTestAab() {
        final BoxCullingFrustum frustum = new BoxCullingFrustum(new BoxCuller(64.0));
        frustum.setPosition(8.0, 8.0, 8.0);

        assertConsistent(frustum, 1L);
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
    void safeZoneAgreesWithTestAab() {
        assertConsistent(safeZone(culler(16.0), culler(96.0)), 20250908L);
        assertConsistent(safeZone(culler(16.0), null), 20250909L);
        assertConsistent(safeZone(culler(96.0), culler(16.0)), 20250910L);
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
