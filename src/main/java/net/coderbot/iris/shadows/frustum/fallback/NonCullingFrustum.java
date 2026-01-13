package net.coderbot.iris.shadows.frustum.fallback;

import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.util.AxisAlignedBB;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.joml.Vector3d;

public class NonCullingFrustum extends Frustrum implements ViewportProvider, Frustum {
	private final Vector3d position = new Vector3d();

	@Override
	public boolean isBoundingBoxInFrustum(AxisAlignedBB aabb) {
		return true;
	}

	@Override
	public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		return true;
	}

	@Override
	public Viewport sodium$createViewport() {
		return new Viewport(this, position.set(xPosition, yPosition, zPosition));
	}
}
