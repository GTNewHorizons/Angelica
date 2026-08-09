package net.coderbot.iris.shadows.frustum;

import net.minecraft.util.AxisAlignedBB;

public class BoxCuller {
	private static final double SNAP = 16.0;
	private static final double PAD = SNAP * 0.5;

	private double maxDistance;

	private double cameraX;
	private double cameraY;
	private double cameraZ;

	private double minAllowedX;
	private double maxAllowedX;
	private double minAllowedY;
	private double maxAllowedY;
	private double minAllowedZ;
	private double maxAllowedZ;

	private double minRelX;
	private double maxRelX;
	private double minRelY;
	private double maxRelY;
	private double minRelZ;
	private double maxRelZ;

	public BoxCuller(double maxDistance) {
		this.maxDistance = maxDistance;
	}

	public void setMaxDistance(double maxDistance) {
		this.maxDistance = maxDistance;
		computeBounds();
	}

	public void setPosition(double cameraX, double cameraY, double cameraZ) {
		this.cameraX = cameraX;
		this.cameraY = cameraY;
		this.cameraZ = cameraZ;
		computeBounds();
	}

	private void computeBounds() {
		final double centerX = snap(cameraX);
		final double centerY = snap(cameraY);
		final double centerZ = snap(cameraZ);
		final double half = maxDistance + PAD;

		this.minAllowedX = centerX - half;
		this.maxAllowedX = centerX + half;
		this.minAllowedY = centerY - half;
		this.maxAllowedY = centerY + half;
		this.minAllowedZ = centerZ - half;
		this.maxAllowedZ = centerZ + half;

		this.minRelX = minAllowedX - cameraX;
		this.maxRelX = maxAllowedX - cameraX;
		this.minRelY = minAllowedY - cameraY;
		this.maxRelY = maxAllowedY - cameraY;
		this.minRelZ = minAllowedZ - cameraZ;
		this.maxRelZ = maxAllowedZ - cameraZ;
	}

	private static double snap(double v) {
		return Math.floor(v / SNAP) * SNAP + SNAP * 0.5;
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
		if (maxX < this.minRelX || minX > this.maxRelX) {
			return true;
		}

		if (maxY < this.minRelY || minY > this.maxRelY) {
			return true;
		}

		return maxZ < this.minRelZ || minZ > this.maxRelZ;
	}
}
