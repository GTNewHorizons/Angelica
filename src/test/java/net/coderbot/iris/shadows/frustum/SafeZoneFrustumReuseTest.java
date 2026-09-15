package net.coderbot.iris.shadows.frustum;

import net.coderbot.iris.shadows.frustum.advanced.SafeZoneCullingFrustum;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static net.coderbot.iris.shadows.frustum.ShadowFrustumTestSupport.SUN_UP;
import static net.coderbot.iris.shadows.frustum.ShadowFrustumTestSupport.culler;
import static net.coderbot.iris.shadows.frustum.ShadowFrustumTestSupport.forEachSampleAab;
import static net.coderbot.iris.shadows.frustum.ShadowFrustumTestSupport.projection;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SafeZoneFrustumReuseTest {
    private static final Vector3f SUN_ANGLED = new Vector3f(0.4f, 0.8f, 0.45f).normalize();

    private static SafeZoneCullingFrustum initialized(Matrix4f view, Vector3f light, BoxCuller voxel, BoxCuller distance) {
        return reinitialized(new SafeZoneCullingFrustum(), view, light, voxel, distance);
    }

    private static SafeZoneCullingFrustum reinitialized(SafeZoneCullingFrustum frustum, Matrix4f view, Vector3f light, BoxCuller voxel, BoxCuller distance) {
        frustum.init(view, projection(), light, voxel, distance);
        frustum.setPosition(8.0, 8.0, 8.0);
        return frustum;
    }

    private static void assertSameResults(SafeZoneCullingFrustum reused, SafeZoneCullingFrustum reference, long seed) {
        forEachSampleAab(seed, (minX, minY, minZ, maxX, maxY, maxZ) -> {
            assertEquals(reference.intersectAab(minX, minY, minZ, maxX, maxY, maxZ),
                reused.intersectAab(minX, minY, minZ, maxX, maxY, maxZ),
                () -> minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ);
            assertEquals(reference.testAab(minX, minY, minZ, maxX, maxY, maxZ),
                reused.testAab(minX, minY, minZ, maxX, maxY, maxZ),
                () -> minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ);
        });
    }

    @Test
    void reinitSwapsCullers() {
        final SafeZoneCullingFrustum reused = initialized(new Matrix4f(), SUN_UP, culler(16.0), culler(96.0));
        reinitialized(reused, new Matrix4f(), SUN_UP, culler(96.0), culler(16.0));

        assertSameResults(reused, initialized(new Matrix4f(), SUN_UP, culler(96.0), culler(16.0)), 20250911L);
    }

    @Test
    void reinitReplacesClippingPlanes() {
        final Matrix4f rotated = new Matrix4f().rotateY((float) Math.toRadians(37.0));

        final SafeZoneCullingFrustum reused = initialized(new Matrix4f(), SUN_UP, culler(16.0), culler(96.0));
        reinitialized(reused, rotated, SUN_ANGLED, culler(24.0), culler(120.0));

        assertSameResults(reused, initialized(rotated, SUN_ANGLED, culler(24.0), culler(120.0)), 20250912L);
    }
}
