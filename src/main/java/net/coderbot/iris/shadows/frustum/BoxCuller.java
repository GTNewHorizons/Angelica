package net.coderbot.iris.shadows.frustum;

import net.minecraft.util.AxisAlignedBB;

public class BoxCuller {
	private double maxDistance;

	private double minAllowedX;
	private double maxAllowedX;
	private double minAllowedY;
	private double maxAllowedY;
	private double minAllowedZ;
	private double maxAllowedZ;

	public BoxCuller(double maxDistance) {
		this.maxDistance = maxDistance;
	}

	public void setMaxDistance(double maxDistance) {
		this.maxDistance = maxDistance;
	}

	public void setPosition(double cameraX, double cameraY, double cameraZ) {
		this.minAllowedX = cameraX - maxDistance;
		this.maxAllowedX = cameraX + maxDistance;
		this.minAllowedY = cameraY - maxDistance;
		this.maxAllowedY = cameraY + maxDistance;
		this.minAllowedZ = cameraZ - maxDistance;
		this.maxAllowedZ = cameraZ + maxDistance;
	}

	public boolean isCulled(AxisAlignedBB aabb) {
		return isCulled((float) aabb.minX, (float) aabb.minY, (float) aabb.minZ,
				(float) aabb.maxX, (float) aabb.maxY, (float) aabb.maxZ);
	}

	public boolean isCulled(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		if (maxX < this.minAllowedX || minX > this.maxAllowedX) {
			return true;
		}

		if (maxY < this.minAllowedY || minY > this.maxAllowedY) {
			return true;
		}

		return maxZ < this.minAllowedZ || minZ > this.maxAllowedZ;
	}

	// View-relative coordinates version
	public boolean isCulledViewRelative(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		if (maxX < -this.maxDistance || minX > this.maxDistance) {
			return true;
		}

		if (maxY < -this.maxDistance || minY > this.maxDistance) {
			return true;
		}

		return maxZ < -this.maxDistance || minZ > this.maxDistance;
	}
}
