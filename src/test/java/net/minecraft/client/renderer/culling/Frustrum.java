package net.minecraft.client.renderer.culling;

import net.minecraft.util.AxisAlignedBB;

public class Frustrum implements ICamera {
    public double xPosition;
    public double yPosition;
    public double zPosition;

    public void setPosition(double x, double y, double z) {
        this.xPosition = x;
        this.yPosition = y;
        this.zPosition = z;
    }

    public boolean isBoxInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return true;
    }

    @Override
    public boolean isBoundingBoxInFrustum(AxisAlignedBB aabb) {
        return this.isBoxInFrustum(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
    }
}
