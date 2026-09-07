package net.coderbot.iris.shadows.frustum;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

final class ShadowFrustumTestSupport {
    static final Vector3f SUN_UP = new Vector3f(0.0f, 1.0f, 0.0f);

    private static final int SAMPLE_COUNT = 20000;

    interface AabConsumer {
        void accept(float minX, float minY, float minZ, float maxX, float maxY, float maxZ);
    }

    private ShadowFrustumTestSupport() {
    }

    static Matrix4f projection() {
        return new Matrix4f().perspective((float) Math.toRadians(90.0), 1.0f, 0.05f, 256.0f);
    }

    static BoxCuller culler(double maxDistance) {
        final BoxCuller culler = new BoxCuller(maxDistance);
        culler.setPosition(8.0, 8.0, 8.0);
        return culler;
    }

    static void forEachSampleAab(long seed, AabConsumer consumer) {
        final Random random = new Random(seed);

        for (int i = 0; i < SAMPLE_COUNT; i++) {
            final float minX = (random.nextFloat() * 2.0f - 1.0f) * 160.0f;
            final float minY = (random.nextFloat() * 2.0f - 1.0f) * 160.0f;
            final float minZ = (random.nextFloat() * 2.0f - 1.0f) * 160.0f;
            final float maxX = minX + random.nextFloat() * 48.0f;
            final float maxY = minY + random.nextFloat() * 48.0f;
            final float maxZ = minZ + random.nextFloat() * 48.0f;

            consumer.accept(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
