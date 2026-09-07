package net.coderbot.iris.shadows.frustum.advanced;

import net.coderbot.iris.shadows.frustum.BoxCuller;
import net.minecraft.util.AxisAlignedBB;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public class SafeZoneCullingFrustum extends AdvancedShadowCullingFrustum {
	private BoxCuller distanceCuller;

	public void init(Matrix4fc playerView, Matrix4fc playerProjection, Vector3f shadowLightVector, BoxCuller voxelCuller, BoxCuller distanceCuller) {
		super.init(playerView, playerProjection, shadowLightVector, voxelCuller);
		this.distanceCuller = distanceCuller;
	}

	@Override
	public boolean supportsOcclusionSearch() {
		return false;
	}

	@Override
	public void setPosition(double cameraX, double cameraY, double cameraZ) {
		if (this.distanceCuller != null) {
			this.distanceCuller.setPosition(cameraX, cameraY, cameraZ);
		}
		super.setPosition(cameraX, cameraY, cameraZ);
	}

	@Override
	public boolean isBoundingBoxInFrustum(AxisAlignedBB aabb) {
		// Cull if outside the overall distance limit
		if (distanceCuller != null && distanceCuller.isCulled(aabb)) {
			return false;
		}

		// If within the voxel safe zone, always render
		if (boxCuller != null && !boxCuller.isCulled(aabb)) {
			return true;
		}

		// Otherwise fall through to advanced frustum culling
		return isVisible(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
	}

	@Override
	public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		// Cull if outside the overall distance limit
		if (distanceCuller != null && distanceCuller.isCulledViewRelative(minX, minY, minZ, maxX, maxY, maxZ)) {
			return false;
		}

		// If within the voxel safe zone, always render
		if (boxCuller != null && !boxCuller.isCulledViewRelative(minX, minY, minZ, maxX, maxY, maxZ)) {
			return true;
		}

		return checkCornerVisibility(minX, minY, minZ, maxX, maxY, maxZ);
	}

	@Override
	public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		int distanceResult = FULLY_INSIDE;

		if (distanceCuller != null) {
			distanceResult = intersectCuller(distanceCuller, minX, minY, minZ, maxX, maxY, maxZ);

			if (distanceResult == OUTSIDE) {
				return OUTSIDE;
			}
		}

		int safeZoneResult = OUTSIDE;

		if (boxCuller != null) {
			safeZoneResult = intersectCuller(boxCuller, minX, minY, minZ, maxX, maxY, maxZ);

			if (safeZoneResult == FULLY_INSIDE && distanceResult == FULLY_INSIDE) {
				return FULLY_INSIDE;
			}
		}

		if (distanceResult == PARTIALLY_INSIDE && safeZoneResult == PARTIALLY_INSIDE) {
			return PARTIALLY_INSIDE;
		}

		final int frustumResult = intersectCorners(minX, minY, minZ, maxX, maxY, maxZ);

		if (safeZoneResult == OUTSIDE && frustumResult == OUTSIDE) {
			return OUTSIDE;
		}

		if (frustumResult == FULLY_INSIDE && distanceResult == FULLY_INSIDE) {
			return FULLY_INSIDE;
		}

		return PARTIALLY_INSIDE;
	}

	private static int intersectCuller(BoxCuller culler, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		if (culler.isCulledViewRelative(minX, minY, minZ, maxX, maxY, maxZ)) {
			return OUTSIDE;
		}

		return culler.isFullyInsideViewRelative(minX, minY, minZ, maxX, maxY, maxZ) ? FULLY_INSIDE : PARTIALLY_INSIDE;
	}
}
