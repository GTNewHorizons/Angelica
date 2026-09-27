package com.gtnewhorizons.angelica.render;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

final class CloudView {

    private final Matrix4f inverseModelView = new Matrix4f();
    private final Vector3f cameraOffset = new Vector3f();
    private float lastForwardX = Float.NaN, lastForwardZ = Float.NaN;
    double pixelsPerRadian;
    double facing;
    float offsetX, offsetY, offsetZ;
    private double sinHalfFovSquared;
    private boolean uncullable;

    void update(Matrix4fc projection, Matrix4fc modelView, int width, int height) {
        pixelsPerRadian = CloudDisc.pixelsPerRadian(width, height, projection);
        final boolean projectionCullable = updateProjection(projection);
        final boolean cameraCullable = updateCamera(modelView);
        uncullable = !projectionCullable || !cameraCullable;
    }

    private boolean updateProjection(Matrix4fc projection) {
        if (!projection.isFinite() || projection.m23() >= 0.0f || projection.m33() != 0.0f
            || projection.m03() != 0.0f || projection.m13() != 0.0f
            || projection.m30() != 0.0f || projection.m31() != 0.0f
            || projection.m01() != 0.0f || projection.m10() != 0.0f
            || projection.m00() == 0.0f || projection.m11() == 0.0f) return false;

        final double tanX = (-projection.m23() + Math.abs(projection.m20())) / Math.abs(projection.m00());
        final double tanY = (-projection.m23() + Math.abs(projection.m21())) / Math.abs(projection.m11());
        final double tanSquared = tanX * tanX + tanY * tanY;
        sinHalfFovSquared = tanSquared / (1.0 + tanSquared);
        return Double.isFinite(sinHalfFovSquared);
    }

    private boolean updateCamera(Matrix4fc modelView) {
        offsetX = offsetY = offsetZ = 0.0f;
        if (!modelView.isFinite() || !modelView.isAffine()) return false;
        modelView.invertAffine(inverseModelView).getTranslation(cameraOffset);
        if (!cameraOffset.isFinite()) return false;
        offsetX = cameraOffset.x;
        offsetY = cameraOffset.y;
        offsetZ = cameraOffset.z;
        if (!rigid(modelView)) return false;
        final float forwardX = -modelView.m02(), forwardZ = -modelView.m22();
        if (forwardX != lastForwardX || forwardZ != lastForwardZ) {
            facing = Math.atan2(forwardZ, forwardX);
            lastForwardX = forwardX;
            lastForwardZ = forwardZ;
        }
        return true;
    }

    double halfSpan(double heightCells, double driftCells) {
        final double radius = CloudDisc.ALWAYS_DRAWN_CELLS - 2.0 - driftCells;
        if (uncullable || radius <= 0.0 || driftCells * 2.0 >= CloudDisc.ALWAYS_DRAWN_CELLS) return Math.PI;
        final double slope = heightCells / radius;
        final double spreadSquared = sinHalfFovSquared * (1.0 + slope * slope);
        if (spreadSquared >= 1.0) return Math.PI;
        return Math.asin(Math.sqrt(spreadSquared)) + CloudDisc.driftAngle(driftCells);
    }

    private static boolean rigid(Matrix4fc matrix) {
        if ((matrix.properties() & Matrix4fc.PROPERTY_ORTHONORMAL) != 0) return true;
        final double xx = matrix.m00() * matrix.m00() + matrix.m01() * matrix.m01() + matrix.m02() * matrix.m02();
        final double yy = matrix.m10() * matrix.m10() + matrix.m11() * matrix.m11() + matrix.m12() * matrix.m12();
        final double zz = matrix.m20() * matrix.m20() + matrix.m21() * matrix.m21() + matrix.m22() * matrix.m22();
        final double xy = matrix.m00() * matrix.m10() + matrix.m01() * matrix.m11() + matrix.m02() * matrix.m12();
        final double xz = matrix.m00() * matrix.m20() + matrix.m01() * matrix.m21() + matrix.m02() * matrix.m22();
        final double yz = matrix.m10() * matrix.m20() + matrix.m11() * matrix.m21() + matrix.m12() * matrix.m22();
        return Math.abs(xx - 1.0) < 1.0e-4 && Math.abs(yy - 1.0) < 1.0e-4 && Math.abs(zz - 1.0) < 1.0e-4
            && Math.abs(xy) < 1.0e-4 && Math.abs(xz) < 1.0e-4 && Math.abs(yz) < 1.0e-4;
    }
}
