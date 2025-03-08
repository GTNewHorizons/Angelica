package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiCullingFrustum;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IOverrideInjector;
import com.seibel.distanthorizons.core.util.math.Mat4f;

/** 
 * Dummy {@link IDhApiCullingFrustum} that allows everything through. <br> 
 * Useful when a frustum is required, but culling shouldn't be done.
 */
public class NeverCullFrustum implements IDhApiCullingFrustum, IDhApiShadowCullingFrustum
{
	//=============//
	// constructor //
	//=============//
	
	public NeverCullFrustum() { }
	
	
	
	//=========//
	// methods //
	//=========//
	
	@Override
	public void update(int worldMinBlockY, int worldMaxBlockY, DhApiMat4f dhWorldViewProjection) { /* update isn't needed */ }
	
	@Override
	public boolean intersects(int lodBlockPosMinX, int lodBlockPosMinZ, int lodBlockWidth, int lodDetailLevel) { return true; }
	
	
	
	//=====================//
	// overridable methods //
	//=====================//
	
	@Override 
	public int getPriority() { return IOverrideInjector.CORE_PRIORITY; }
	
}
