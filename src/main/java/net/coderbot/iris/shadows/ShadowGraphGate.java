package net.coderbot.iris.shadows;

public final class ShadowGraphGate {

    public static final float SHADOW_ANGLE_GRAPH_DELTA = 0.0005f;

    private ShadowGraphGate() {}

    public static boolean shouldMarkDirty(float last, float current) {
        float delta = current - last;
        if (delta > 0.5f) delta -= 1.0f;
        if (delta < -0.5f) delta += 1.0f;
        return Float.isNaN(last) || Math.abs(delta) > SHADOW_ANGLE_GRAPH_DELTA;
    }
}
