package me.jellysquid.mods.sodium.client.util.math;

import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Vector3f;

public interface Matrix3fExtended {
    /**
     * Applies the specified rotation to this matrix in-place.
     *
     * @param quaternion The quaternion to rotate this matrix by
     */
    void rotate(Quaternion quaternion);

    int computeNormal(ForgeDirection dir);

    float transformVecX(float x, float y, float z);

    float transformVecY(float x, float y, float z);

    float transformVecZ(float x, float y, float z);

    default float transformVecX(Vector3f dir) {
        return this.transformVecX(dir.x, dir.y, dir.z);
    }

    default float transformVecY(Vector3f dir) {
        return this.transformVecY(dir.x, dir.y, dir.z);
    }

    default float transformVecZ(Vector3f dir) {
        return this.transformVecZ(dir.x, dir.y, dir.z);
    }
}
