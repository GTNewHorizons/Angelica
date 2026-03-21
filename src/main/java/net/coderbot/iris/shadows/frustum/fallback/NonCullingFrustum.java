package net.coderbot.iris.shadows.frustum.fallback;

import cpw.mods.fml.common.Optional;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.util.AxisAlignedBB;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.joml.Vector3d;

@Optional.Interface(modid = "distanthorizons", iface = "com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum")
public class NonCullingFrustum extends Frustrum implements ViewportProvider, Frustum, IDhApiShadowCullingFrustum {
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

    @Optional.Method(modid = "distanthorizons")
    @Override
    public void update(int worldMinBlockY, int worldMaxBlockY, DhApiMat4f worldViewProjection) {

    }

    @Optional.Method(modid = "distanthorizons")
    @Override
    public boolean intersects(int lodBlockPosMinX, int lodBlockPosMinZ, int lodBlockWidth, int lodDetailLevel) {
        return true;
    }

}
