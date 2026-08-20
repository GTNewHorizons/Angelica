package net.coderbot.iris.shadows;

public final class ShadowGraphGate {

    public static final float DEFAULT_ANGLE_DELTA_DEGREES = 0.03f;

    public static final float DEFAULT_HORIZON_SCALE = 0.5f;

    private ShadowGraphGate() {}

    public static float degreesToRotation(float degrees) {
        return degrees / 360.0f;
    }

    public static boolean shouldMarkDirty(float last, float current, float delta) {
        float d = current - last;
        if (d > 0.5f) d -= 1.0f;
        if (d < -0.5f) d += 1.0f;
        return Float.isNaN(last) || Math.abs(d) > delta;
    }

    public static float deltaForElevation(float sinElevation, float baseDelta, float minElevationScale) {
        return baseDelta * Math.max(minElevationScale, sinElevation * sinElevation);
    }
}
