package me.jellysquid.mods.sodium.client.util.math;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.api.util.NormI8;
import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class MatrixUtil {
    public static int computeNormal(Matrix3f normalMatrix, ForgeDirection facing) {
        return ((Matrix3fExtended) (Object) normalMatrix).computeNormal(facing);
    }

    public static Matrix4fExtended getExtendedMatrix(Matrix4f matrix) {
        return (Matrix4fExtended) (Object) matrix;
    }

    public static Matrix3fExtended getExtendedMatrix(Matrix3f matrix) {
        return (Matrix3fExtended) (Object) matrix;
    }

    public static int transformPackedNormal(int norm, Matrix3f matrix) {
        Matrix3fExtended mat = MatrixUtil.getExtendedMatrix(matrix);

        float normX1 = NormI8.unpackX(norm);
        float normY1 = NormI8.unpackY(norm);
        float normZ1 = NormI8.unpackZ(norm);

        float normX2 = mat.transformVecX(normX1, normY1, normZ1);
        float normY2 = mat.transformVecY(normX1, normY1, normZ1);
        float normZ2 = mat.transformVecZ(normX1, normY1, normZ1);

        return NormI8.pack(normX2, normY2, normZ2);
    }
}
