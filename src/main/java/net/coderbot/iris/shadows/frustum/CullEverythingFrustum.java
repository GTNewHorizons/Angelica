package net.coderbot.iris.shadows.frustum;

import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.util.AxisAlignedBB;
import org.joml.Matrix4f;

public class CullEverythingFrustum extends Frustrum {

	// for Sodium
	public boolean fastAabbTest(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		return false;
	}

	// For Immersive Portals
	// We return false here since isVisible is going to return false anyways.
	public boolean canDetermineInvisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		return false;
	}

	@Override
	public boolean isBoundingBoxInFrustum(AxisAlignedBB aabb) {
		return false;
	}
}
