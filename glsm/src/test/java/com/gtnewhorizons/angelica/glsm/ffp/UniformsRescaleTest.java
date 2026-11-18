package com.gtnewhorizons.angelica.glsm.ffp;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UniformsRescaleTest {

    private static float rescaleFactorFor(float sx, float sy, float sz) {
        final Matrix3f normalMatrix = new Matrix3f();
        new Matrix4f().rotateY(0.7f).scale(sx, sy, sz).normal(normalMatrix);
        return Uniforms.rescaleFactor(normalMatrix, new Vector3f());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "wrong normal matrix column, 4, 2, 8, 8",
        "uniform scale, 2, 2, 2, 2",
        "a degenerate normal matrix must not divide by zero, 1, 1, 0, 1",
    })
    void rescaleFactorUsesTheInverseModelviewBottomRow(String label, float sx, float sy, float sz, float expected) {
        assertEquals(expected, rescaleFactorFor(sx, sy, sz), 1.0e-5f, label);
    }
}
