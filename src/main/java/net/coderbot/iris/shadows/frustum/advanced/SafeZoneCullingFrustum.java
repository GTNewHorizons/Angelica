package net.coderbot.iris.shadows.frustum.advanced;

import net.coderbot.iris.shadows.frustum.BoxCuller;
import net.minecraft.util.AxisAlignedBB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class SafeZoneCullingFrustum extends AdvancedShadowCullingFrustum {
	private final BoxCuller distanceCuller;

	public SafeZoneCullingFrustum(Matrix4f playerView, Matrix4f playerProjection, Vector3f shadowLightVector, BoxCuller voxelCuller, BoxCuller distanceCuller) {
		super();
		init(playerView, playerProjection, shadowLightVector, voxelCuller);
		this.distanceCuller = distanceCuller;
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
}
