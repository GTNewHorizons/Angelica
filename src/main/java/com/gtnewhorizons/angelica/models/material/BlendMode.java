package com.gtnewhorizons.angelica.models.material;

import com.gtnewhorizons.angelica.compat.toremove.RenderLayer;

/**
 * Defines how sprite pixels will be blended with the scene.
 */
public enum BlendMode {
	/**
	 * Emulate blending behavior of {@code BlockRenderLayer} associated with the block.
	 */
	DEFAULT(null),

	/**
	 * Fully opaque with depth test, no blending. Used for most normal blocks.
	 */
	SOLID(RenderLayer.solid()),

	/**
	 * Pixels with alpha &gt; 0.5 are rendered as if {@code SOLID}. Other pixels are not rendered.
	 * Texture mip-map enabled.  Used for leaves on modern, unused in 1.7.10.
	 */
	//CUTOUT_MIPPED(RenderLayer.cutout()),

	/**
	 * Pixels with alpha &gt; 0.5 are rendered as if {@code SOLID}. Other pixels are not rendered.
	 * Texture mip-map disabled.  Used for iron bars, glass and other cutout sprites with hard edges.
	 */
	CUTOUT(RenderLayer.cutout()),

	/**
	 * Pixels are blended with the background according to alpha color values. Some performance cost,
	 * use in moderation. Texture mip-map enabled.  Used for stained glass.
	 */
	TRANSLUCENT(RenderLayer.translucent());

	public final RenderLayer blockRenderLayer;

	BlendMode(RenderLayer blockRenderLayer) {
		this.blockRenderLayer = blockRenderLayer;
	}

	public static BlendMode fromRenderLayer(RenderLayer renderLayer) {
		if (renderLayer == RenderLayer.solid()) {
			return SOLID;
		/*} else if (renderLayer == RenderLayer.getCutoutMipped()) {
			return CUTOUT_MIPPED;*/
		} else if (renderLayer == RenderLayer.cutout()) {
			return CUTOUT;
		} else if (renderLayer == RenderLayer.translucent()) {
			return TRANSLUCENT;
		} else {
			return DEFAULT;
		}
	}
}
