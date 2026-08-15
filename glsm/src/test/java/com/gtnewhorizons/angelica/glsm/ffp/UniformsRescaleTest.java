package com.gtnewhorizons.angelica.glsm.ffp;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UniformsRescaleTest {

    private static float rescaleFactorFor(float sx, float sy, float sz) {
        final Matrix3f normalMatrix = new Matrix3f();
        new Matrix4f().rotateY(0.7f).scale(sx, sy, sz).normal(normalMatrix);
        return Uniforms.rescaleFactor(normalMatrix, new Vector3f());
    }

    @Test
    void rescaleFactorUsesTheInverseModelviewBottomRow() {
        assertEquals(8.0f, rescaleFactorFor(4.0f, 2.0f, 8.0f), 1.0e-5f, "wrong normal matrix column");
    }

    @Test
    void uniformScaleIsRowIndependent() {
        assertEquals(2.0f, rescaleFactorFor(2.0f, 2.0f, 2.0f), 1.0e-5f);
    }

    @Test
    void singularMatrixFallsBackToUnitScale() {
        assertEquals(1.0f, rescaleFactorFor(1.0f, 1.0f, 0.0f), 1.0e-5f, "a degenerate normal matrix must not divide by zero");
    }
}
