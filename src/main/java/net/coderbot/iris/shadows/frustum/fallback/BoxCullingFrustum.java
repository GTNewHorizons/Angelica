package net.coderbot.iris.shadows.frustum.fallback;

import net.coderbot.iris.shadows.frustum.BoxCuller;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.util.AxisAlignedBB;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.joml.Vector3d;

public class BoxCullingFrustum extends Frustrum implements ViewportProvider, Frustum {
	private final BoxCuller boxCuller;
	private final Vector3d position = new Vector3d();

	public BoxCullingFrustum(BoxCuller boxCuller) {
		this.boxCuller = boxCuller;
	}

	@Override
	public void setPosition(double cameraX, double cameraY, double cameraZ) {
		super.setPosition(cameraX, cameraY, cameraZ);
		boxCuller.setPosition(cameraX, cameraY, cameraZ);
	}

	@Override
	public boolean isBoundingBoxInFrustum(AxisAlignedBB aabb) {
		return !boxCuller.isCulled(aabb);
	}

	@Override
	public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		return !boxCuller.isCulledViewRelative(minX, minY, minZ, maxX, maxY, maxZ);
	}

	@Override
	public Viewport sodium$createViewport() {
		return new Viewport(this, position.set(xPosition, yPosition, zPosition));
	}
}
