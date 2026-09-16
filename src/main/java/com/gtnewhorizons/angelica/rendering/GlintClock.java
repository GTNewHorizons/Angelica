package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.joml.Matrix4fc;

public final class GlintClock {

    private static final double SPEED0 = 0.001;
    private static final double SPEED1 = 0.001 + 0.003;
    private static final double SIN0 = Math.sin(Math.toRadians(30.0));
    private static final double COS0 = Math.cos(Math.toRadians(30.0));
    private static final double SIN1 = Math.sin(Math.toRadians(-30.0));
    private static final double COS1 = Math.cos(Math.toRadians(-30.0));

    private static long millis;
    private static float armorU0, armorV0, armorU1, armorV1;

    public static void beginFrame(long nowMillis) {
        millis = nowMillis;
        final double ticks = nowMillis / 50.0;

        final double f0 = ticks * SPEED0 * 20.0;
        final double du0 = frac(-f0 * SIN0 / 3.0);
        final double dv0 = frac(f0 * COS0 / 3.0);
        armorU0 = (float) (3.0 * (COS0 * du0 + SIN0 * dv0));
        armorV0 = (float) (3.0 * (-SIN0 * du0 + COS0 * dv0));

        final double f1 = ticks * SPEED1 * 20.0;
        final double du1 = frac(-f1 * SIN1 / 3.0);
        final double dv1 = frac(f1 * COS1 / 3.0);
        armorU1 = (float) (3.0 * (COS1 * du1 + SIN1 * dv1));
        armorV1 = (float) (3.0 * (-SIN1 * du1 + COS1 * dv1));
    }

    public static long millis() {
        return millis;
    }

    static boolean secondArmorLayer(Matrix4fc textureMatrix) {
        return textureMatrix.m01() < 0f;
    }

    public static void translateArmorGlint() {
        if (secondArmorLayer(GLStateManager.getMatrixStack())) {
            GLStateManager.glTranslatef(armorU1, armorV1, 0f);
        } else {
            GLStateManager.glTranslatef(armorU0, armorV0, 0f);
        }
    }

    private static double frac(double x) {
        return x - Math.floor(x);
    }

    private GlintClock() {}
}
